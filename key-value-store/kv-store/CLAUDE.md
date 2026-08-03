# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Single-node key-value store: a disk-backed LSM-style storage engine (write-ahead log +
memtable + SSTable + bloom filter) behind a `put`/`get` REST API. Java 21 / Spring Boot 3.3.
This is phase 1 of the design in `../key-value-storage.md` -- see "Not implemented / future
phases" in `README.md` for what's deliberately out of scope (partitioning, replication,
quorum, vector clocks, gossip, hinted handoff, Merkle-tree anti-entropy, compaction, deletes).

**No Docker required.** Unlike the sibling `rate-limiter` service (which needs Testcontainers
+ Docker for its Redis-backed integration tests), this service has zero external dependencies
-- everything is a plain JUnit 5 test, including the full HTTP round-trip test
(`KvStoreApplicationTest`, `@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate`).

## Commands

```bash
mvn test                                              # run the full test suite (plain JUnit 5, no Docker)
mvn test -Dtest=StorageEngineTest                     # run a single test class
mvn test -Dtest=StorageEngineTest#crashRecoveryReplaysUnflushedWalAndKeepsFlushedSSTables
mvn spring-boot:run                                   # run the app locally on :8080
mvn package                                           # build the jar
```

Data lives under `kvstore.data-dir` (default `./data`, relative to the working directory
the app is started from). Delete that directory to start from a clean slate.

## Architecture

**Write path**: `KeyValueController.put` -> `StorageEngine.put` -> append to the
write-ahead log (`WriteAheadLog`, fsynced) -> apply to the in-memory `MemTable`. When the
memtable's byte size crosses `kvstore.flush-threshold-bytes`, `StorageEngine.flush()`
swaps in a fresh empty memtable, writes the old one's sorted contents to a new immutable
SSTable file plus a bloom filter (`SSTableWriter`), registers it, then rotates (truncates)
the WAL -- in that order, so a crash between "SSTable written" and "WAL rotated" just
causes a harmless redundant WAL replay on next startup, never data loss.

**Read path**: `StorageEngine.get` checks the active memtable, then (if a flush is
in-flight) the memtable currently being flushed, then every on-disk SSTable newest-first
-- each SSTable's bloom filter is checked first to usually skip a full scan, and reading
one that does need scanning stops early once a key sorts past the target (files are
stored in sorted order).

**Crash recovery** (`StorageEngine.recover()`, run once at startup before the bean is
published): rebuilds the SSTable index from whatever `.data`/`.bloom` file pairs already
exist on disk (rebuilding a missing `.bloom` by rescanning its `.data` file), then replays
the WAL into a fresh memtable. If the WAL's tail is torn (a write interrupted mid-append
by a crash), replay stops at the last valid, checksummed record and the file is truncated
to that point *before* any new append is allowed -- otherwise a later replay would hit the
same garbage first and silently drop everything written after the crash.

**Concurrency**: `MemTable` is a `ConcurrentSkipListMap`, safe for concurrent put/get with
no external locking. `WriteAheadLog.append` is `synchronized` (a deliberate single-writer
bottleneck -- concurrent unsynchronized writes to the same file could interleave partial
frames). `StorageEngine`'s `ReentrantReadWriteLock` guards *only* the active/flushing
memtable pointer swap (a few nanoseconds), not the flush's actual disk write -- so `get()`
never blocks on I/O, and `put()` is only ever blocked for the swap itself plus the WAL's
rotate/fsync, never for the duration of a flush.

## File formats

Both the WAL and SSTable data files share one binary frame
(`com.example.kvstore.format.RecordCodec`), big-endian, UTF-8 strings:

```
keyLength(int32) | keyBytes | valueLength(int32) | valueBytes | crc32(int32)
```

The trailing CRC32 covers everything before it, so a torn/corrupt record at a file's tail
is detected on read rather than silently corrupting a value. `RecordCodec.decode` never
throws for this -- it returns `Optional.empty()`, which callers treat as "no more valid
records" (the expected way a WAL ends after a crash).

- **WAL** (`<data-dir>/wal.log`): a raw append-ordered sequence of frames, no header, no
  delete/tombstone marker (every record is an unconditional put -- see "Not implemented").
- **SSTable** (`<data-dir>/sstables/sstable-<20-digit seq>.data` +
  matching `.bloom`): frames in ascending key order (trivial -- the memtable is already
  sorted). No sparse index -- reads sequentially scan with early exit.
- **Bloom filter** (`.bloom` file): `bitSizeInBits(int32) | numHashFunctions(int32) | bits`.
  Custom implementation (`com.example.kvstore.bloom.BloomFilter`) -- no external
  bloom-filter/hashing library -- using Kirsch-Mitzenmacher double hashing over two
  independent FNV-1a 64-bit hashes.

## Testing conventions

Plain `*Test.java` naming throughout (no `*IT.java` -- that suffix means "needs Docker"
in the sibling rate-limiter service and would be misleading here). `@TempDir` for
filesystem-backed tests (`WriteAheadLogTest`, `SSTableWriterReaderTest`,
`StorageEngineTest`). `StorageEngineTest`'s crash-recovery test builds a `StorageEngine`,
writes data, closes it *without* a graceful final flush (simulating a crash), then builds
a fresh `StorageEngine` on the same directory and asserts every key survives.

`KeyValueControllerTest` supplies `StorageEngineProperties` via a real `@TestConfiguration`
bean rather than `@MockBean` -- it's a record (implicitly final), which Mockito's default
subclass mock maker cannot mock.

`KvStoreApplicationTest` uses a manually created temp directory (`Files.createTempDirectory`,
not JUnit's `@TempDir`) for the same reason noted in that file's Javadoc: the storage
engine's WAL file handle lives inside Spring's cached test `ApplicationContext`, and
`@TempDir`'s cleanup is not guaranteed to run after that context (and the file handle) is
closed -- on Windows that race makes the directory deletion fail.
