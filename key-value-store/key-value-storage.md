# Design a key-value store

Also known as key-value database. Each unique identifier is stored as a key with its associated value
A key-value pair must have:
	- key: plain text or hashed
	- value: an object that could be: string, list,...
	
## Requirement
The key-value store need to support operations:
- put(key, value)
- get(key)

Characteristics:
- Size of a key-value pair is small: < 10KB
- Able to store big data. i.e support unlimited number of key-value pair
- High availability: System response quickly even during failure
- High scalability: Can be scaled to support large data set
- Automatic scaling
- Tunable consitency
- Low latency

## Design choice
Distributed server
- Use hash table
- Data compression
- Store frequently access data on memory, the rest on disk

Why?
- Single server reach its capacity quickly

Distributed key-value store = Distributed hash table
Since network failure is unavoidable => Can only support CP or AP base on CAP theory

- CP system: on partition, only the minority side (nodes without quorum) blocks writes/goes unavailable. The majority side (has quorum) keeps accepting reads/writes normally, since 2/3+ nodes agreeing guarantees no conflicting write can happen elsewhere. Minority side stays unavailable until it rejoins and resyncs.

- AP system: on partition, all sides keep accepting reads (possibly stale) and writes, regardless of quorum. Conflicting writes get reconciled (e.g. last-write-wins, vector clocks, CRDTs) once the partition heals and nodes resync.

## System component

1. Data partition
Use consistent hashing which has advantages:
	- *Automatic scaling*: server could be added and removed automatically depending on the load
	- *Heterogeneity*: base on server capacity, it could hold more virtual node
	
2. Data replication
Data must be replicated asynchronously over N server (configurable)
- Logic: after a key is mapped to hash ring, go on clockwise from that postion and choose first N servers to store data copies. But choose physic servers instead of virtual server for storing
- Notes: Better reliablility is to store replica on diffrent data centers can connect them with high-speed network

3. Consistency
Use Quorum consensus to guarantee consistency for both read and write operations.
N: number of replicas
W: a write quorum of size W. i.e a write operation is successful if acknowledged from W replicas
R: a read quorum of size R. i.e a read operation is successful if has queried from at least R replicas

Decision matrix:
W = 1 or R = 1 quick operation but less consistency
W > 1 or R > 1 slower but better consistency
R = 1 and W = N, the system is optimized for a fast read.
W = 1 and R = N, the system is optimized for fast write.
W + R > N, strong consistency is guaranteed (Usually N = 3, W = R = 2).
W + R <= N, strong consistency is not guaranteed.

Example: 
Concretely with N=3:
W=2, R=2 → 2+2=4 > 3 → strong consistency guaranteed
Quorum overlap guarantees you see it, not that ordering is unambiguous. Still need conflict resolutions to resolve the write operation

4. Consistency model
- Strong consistency: any read operation returns a value corresponding to the result of the most updated write data item. A client never sees out-of-date data.
- Weak consistency: subsequent read operations may not see the most updated value.
- Eventual consistency: this is a specific form of weak consistency. Given enough time, all updates are propagated, and all replicas are consistent.

Production choice: 	*Eventual consistency* which support highly available system

5. Inconsistency resolution: versioning
Versioning means treating each data modification as a new immutable version of data.
Solution to solve inconsistency problems: Versioning and vector clocks
write
                      │
        ① write handled by Sx
                      │
                      ▼
              D1([Sx, 1])
                      │
        ② write handled by Sx
                      │
                      ▼
              D2([Sx, 2])
                      │
        ┌─────────────┴─────────────┐
③ write handled by Sy        ④ write handled by Sz
        │                             │
        ▼                             ▼
D3([Sx, 2], [Sy, 1])          D4([Sx, 2], [Sz, 1])
        │                             │
        └─────────────┬─────────────┘
              ⑤ reconciled and written by Sx
                       │
                       ▼
         D5([Sx, 3], [Sy, 1], [Sz, 1])

Pros: Can resolve conflict
Cons: 
- Move the complexity to client since it need to implement conflict resolution logic
- [server:version] pairs grow rapidly -> need to remove oldest pairs -> need good tune of the threshold to efficiently reconciliation

6. Handling failures

6.1 Failure detection
Usually require 2 independent sources of information to mark a server down
Solution: *gossip protocol*
- Each node has membership list: [member_id, heartbeat_counter]
- Each node periodically increase heartbeat_counter
- Each node periodically send heartbeat to a set of random node
- Once node receive heartbeats, membership list is updated to lastest info
- If heartbeat is not increased for more than predefined periods -> considered as offline

6.2 Handling temporary failures
After failures detected -> system deploy mechanisms to ensure availability
*sloppy quorum*: the system chooses the first W healthy servers for writes and first R healthy servers for reads on the hash ring. Offline servers are ignored
If a server is unavailable due to network or server failures, another server will process requests temporarily. When the down server is up, changes will be pushed back to achieve data consistency.

6.3 Handling permanent failures
The replica is permanently unavailable -> need to keep replicas in sync
Solution: *Anti-entropy* with Merkle tree
- Build the hash tree from group up: node hash = hash of all children
- Compare the root -> branch -> leaf: detect the bucket that are not synchronized and do the synchronization

6.4 Handling data center outage
- Replicate data through multiple data center

## System architecture diagram
Decentralized the system, each node will have same set of responsibility
- Client communication with key-value store using simple API: get(key), put(key, value)
- Coordinator is a node that act as proxy between client and key-value store
- Node are distributed on a ring using consisten hashing
- Completely decentralized so adding and moving nodes are automatic
- Data is replicated at multiple nodes
- No single point of failure

Each node responsibility:
- Client API
- Conflict resolution
- Replication
- Failure detection
- Failure repair mechanism
- Storage engine

### Write path
1. Write request persisted on a commit log file
2. Data saved in memory cache
3. When memory cache is full or reach predefined threshold -> flush data to SSTable on disk

### Read path
1. Check if data is in memory. If yes return or else go to step 2
2. Check the bloom filter
3. The bloom filter help to figure out which SSTable might contain the key
4. SSTables return the result of data set
5. The result of data is returned to the client

## Sumarize

| Goal/Problems              | Technique                                          |
|-----------------------------|-----------------------------------------------------|
| Ability to store big data   | Use consistent hashing to spread the load across servers |
| High availability reads     | Data replication, multi-data center setup           |
| Highly available writes     | Versioning and conflict resolution with vector clocks |
| Dataset partition           | Consistent hashing                                   |
| Incremental scalability     | Consistent hashing                                   |
| Heterogeneity                | Consistent hashing                                   |
| Tunable consistency          | Quorum consensus                                     |
| Handling temporary failures  | Sloppy quorum and hinted handoff                     |
| Handling permanent failures  | Merkle tree                                          |
| Handling data center outage  | Cross-data center replication                        |