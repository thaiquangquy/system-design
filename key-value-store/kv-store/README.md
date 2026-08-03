# Key-Value Store

A single-node key-value store: a disk-backed LSM-style storage engine (write-ahead log +
memtable + SSTable + bloom filter) behind a simple `put(key, value)` / `get(key)` REST API.

Built with Java 21 and Spring Boot 3.3. This is **phase 1** of the design in
[`../key-value-storage.md`](../key-value-storage.md) -- the storage engine and API only.
Everything about running this as an actual *distributed* store (partitioning, replication,
quorum, gossip, anti-entropy) is deliberately not built yet; see
["Not implemented / future phases"](#not-implemented--future-phases) below.

## High-level architecture

```
Write path:
  PUT /api/v1/kv/{key}
        │
        ▼
  KeyValueController
        │
        ▼
  StorageEngine.put(key, value)
        │
        ├──▶ WriteAheadLog.append(key, value)   (fsynced -- durable before we touch memory)
        │
        ▼
  MemTable.put(key, value)   (ConcurrentSkipListMap, in memory)
        │
        │  size >= kvstore.flush-threshold-bytes ?
        ▼
  flush(): swap in a fresh empty MemTable, then (outside the swap lock, off the write's
  critical path):
        │
        ├──▶ SSTableWriter.write(...)  -->  sstables/sstable-<seq>.data + .bloom
        │
        └──▶ WriteAheadLog.rotate()   (only after the SSTable above is durably registered)


Read path:
  GET /api/v1/kv/{key}
        │
        ▼
  StorageEngine.get(key)
        │
        ├──▶ active MemTable ────────────────────────── found? return
        │
        ├──▶ in-flight "flushing" MemTable (if any) ─── found? return
        │        (closes the race window during a flush -- see Concurrency below)
        │
        └──▶ SSTables, newest-first:
                   bloom filter says "definitely absent"? skip the file entirely
                   otherwise: sequential scan, early-exit once a key sorts past target
                                                          found? return
        │
        ▼
  404 Key Not Found
```

## Code structure

```
src/main/java/com/example/kvstore/
├── KvStoreApplication.java
├── format/
│   ├── RecordCodec.java          Shared WAL + SSTable binary frame: encode/decode
│   └── KvRecord.java             record(key, value)
├── wal/
│   └── WriteAheadLog.java        Append (fsynced, synchronized), replay (crash recovery), rotate
├── memtable/
│   └── MemTable.java             ConcurrentSkipListMap + byte-size accounting for the flush threshold
├── bloom/
│   └── BloomFilter.java          Custom bit-array + double-hashing bloom filter (no external lib)
├── sstable/
│   ├── SSTableWriter.java        Flushes sorted memtable entries -> immutable .data + .bloom files
│   └── SSTableReader.java        Bloom-check + sequential-scan-with-early-exit lookup
├── engine/
│   ├── StorageEngine.java        Coordinates the write/read path, flush-and-swap, recover()
│   ├── StorageEngineProperties.java   @ConfigurationProperties(prefix = "kvstore")
│   └── StorageEngineConfig.java  Builds the engine bean, runs recover() before publishing it
├── api/
│   ├── KeyValueController.java   PUT/GET /api/v1/kv/{key}
│   ├── dto/                      PutValueRequest, KeyValueResponse
│   ├── KeyNotFoundException.java     -> 404
│   └── ValueTooLargeException.java   -> 413
└── exception/
    └── GlobalExceptionHandler.java   @RestControllerAdvice mapping the exceptions above (+ 400 on invalid body)
```

**Suggested reading order**: `RecordCodec` → `WriteAheadLog` → `MemTable` → `BloomFilter`
→ `SSTableWriter`/`SSTableReader` → `StorageEngine` (the coordinator, and the trickiest
part -- flush-and-swap and crash recovery) → `KeyValueController`.

## File formats

Both the WAL and SSTable data files share one binary frame (big-endian, UTF-8 strings):

```
keyLength(int32) | keyBytes | valueLength(int32) | valueBytes | crc32(int32)
```

The trailing CRC32 covers everything before it, letting a torn/corrupt record at a file's
tail (from a crash mid-write) be detected on read instead of silently returning garbage.
Decoding never throws for a torn tail or CRC mismatch -- it signals "no more valid
records", which is exactly how a write-ahead log is expected to end after a crash.

- **Write-ahead log** (`<data-dir>/wal.log`): a raw, append-ordered sequence of frames.
  No header, no delete marker -- every record is an unconditional put (see below: no
  delete support in this phase).
- **SSTable** (`<data-dir>/sstables/sstable-<20-digit sequence>.data` + a paired `.bloom`
  file): frames in ascending key order (the memtable is already sorted, so this falls
  out for free). There's no sparse index, so a lookup that isn't ruled out by the bloom
  filter sequentially scans the file -- but stops as soon as a key sorts past the
  target, since nothing later in the file can match.
- **Bloom filter** (`.bloom`): `bitSizeInBits(int32) | numHashFunctions(int32) | bits`.
  A small, self-contained implementation (Kirsch-Mitzenmacher double hashing over two
  independent 64-bit FNV-1a hashes) rather than a dependency like Guava.

## Configuration

| Property | Env var | Default | Purpose |
|---|---|---|---|
| `kvstore.data-dir` | `KVSTORE_DATA_DIR` | `./data` | Root directory for `wal.log` + `sstables/` |
| `kvstore.flush-threshold-bytes` | `KVSTORE_FLUSH_THRESHOLD_BYTES` | `4194304` (4MB) | Approx. memtable byte size that triggers a flush |
| `kvstore.bloom-expected-insertions` | `KVSTORE_BLOOM_EXPECTED_INSERTIONS` | `10000` | Expected keys per SSTable, used to size each flush's bloom filter |
| `kvstore.bloom-false-positive-rate` | `KVSTORE_BLOOM_FALSE_POSITIVE_RATE` | `0.01` | Target bloom filter false-positive rate |
| `kvstore.max-value-bytes` | `KVSTORE_MAX_VALUE_BYTES` | `1048576` (1MB) | Hard ceiling on a single PUT's value size (a defensive guard, not the same as the design spec's soft "<10KB expected" assumption, which is not separately enforced) |
| `server.port` | -- | `8080` | HTTP port |

## Running

```bash
mvn spring-boot:run
```

The app listens on `http://localhost:8080`. See [`demo.http`](demo.http) for a full
walkthrough (put, get, overwrite, not-found, oversized-value, invalid-body).

```bash
curl -X PUT http://localhost:8080/api/v1/kv/hello \
  -H "Content-Type: application/json" \
  -d '{"value": "world"}'

curl http://localhost:8080/api/v1/kv/hello
# {"key":"hello","value":"world"}

curl http://localhost:8080/api/v1/kv/does-not-exist
# 404: {"error":"Key 'does-not-exist' not found"}
```

Data is written under `kvstore.data-dir` (default `./data`). To exercise a flush, put
enough distinct keys to cross `flush-threshold-bytes`, then check
`data/sstables/` for a new `sstable-*.data`/`.bloom` pair. To exercise crash recovery,
kill the process (don't call any shutdown endpoint) and restart it pointed at the same
`data/` directory -- previously-put keys, including ones only in the WAL (never flushed),
should still be readable.

### Docker

```bash
docker build -t kv-store .
docker run -p 8080:8080 -v "$(pwd)/data:/app/data" -e KVSTORE_DATA_DIR=/app/data kv-store
```

Unlike the stateless `rate-limiter` service, persistence to a mounted host path is the
entire point here -- run without the volume mount and all data is lost when the container exits.

## Build and test

No Docker required -- every test, including the full HTTP round-trip test, is plain JUnit 5.

```bash
mvn test                                              # full suite
mvn test -Dtest=StorageEngineTest                     # a single test class
mvn package                                           # build the jar
```

## Not implemented / future phases

This phase covers only the single-node storage engine and its `put`/`get` API. Everything
below is part of the full design in [`../key-value-storage.md`](../key-value-storage.md)
but deliberately not built here:

- Consistent hashing / data partitioning across nodes
- Replication (sync or async) across N servers
- Quorum consensus (tunable N/W/R read/write consistency)
- Vector clocks / version vectors for conflict resolution
- Gossip-based failure detection
- Sloppy quorum + hinted handoff (temporary-failure handling)
- Merkle-tree anti-entropy (permanent-failure / replica-repair handling)
- Cross-data-center replication
- **SSTable compaction** -- SSTables accumulate indefinitely; reads still correctly
  resolve newest-first, but disk usage and the number of files searched per miss both
  grow unbounded without it
- **A sparse SSTable index** -- lookups that aren't ruled out by the bloom filter do a
  full sequential scan of the file; an offset index would make this closer to O(log n)
- **Delete / tombstone support** -- the design spec's requirements are only `put`/`get`
