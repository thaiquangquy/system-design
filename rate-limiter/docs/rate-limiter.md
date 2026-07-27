# Rate Limiter

## Design

### Where to put the rate Limiter
 - Prefer server-side, put in the middle of client and API servers
 - Moderm approach is put rate limiter within API gateway
 
### Algorithms for reate limiting
 - *Token bucket*: Simple but powerful
	- Params: bucket size + refill rate
	- When request come -> if bucket have token -> request will be process or else it will be drop
	- number of bucket
		- per API & user: diffrence API will have diffrence bucket with difference parameter
		- per IP
		- per global bucket
	- Pros:
		- Simple to implement
		- Memory efficient
		- Allow burst of traffic
	- Cons:
		- 2 parameters make it hard to tune
		
 - *Leaking bucket*
	- Params: bucket size + overflow rate
	- When request come -> if the bucket queue is not full -> request will be push to queue or else drop -> queue will be pop and process with a fixed rate(= overflow rate)
	- Pros:
		- Fixed queue size -> memory efficient
		- Allow the request to be processed at a fixed pace - stable
	- Cons:
		- Burst traffic will drop new request
		- Hard to tune the parameter
		
 - *Fixed window counter*
	- Params: fix-sized time window, max request threshold, counter for each window
	- During a window time, only allow number of request < threshold -> when a request come -> counter is increase -> if counter > max threshold then request is droped -> counter reset at the start of new windows
	- Pros:
		- Memory efficient
		- Allow fixed page at a defined time window
	- Cons:
		- Weak on edges of time windows -> spike traffic could cause more request than defined quota
		eg: 2 requests/min, reset at round minute value:
			00:01:00 -> 00:02:00 -> request at 00:01:50 -> allow -> request at 00:01:55 -> allow
			00:01:30 -> 00:02:30 -> request at 00:02:05 -> allow -> request at 00:02:10 -> allow
			4 request within 1 min time windows (excact 20 sec) > 2 as defined quota
	
 - *Sliding window log*
	- Params: fix-sized time window, max request threshold, counter for each window, a redis cache
	- Request come -> remove outdated timestamps that < start of current time window -> add timestamp of the new request to log -> log size <= threshold then allow request or else drop
	- This algorithms strict the time windows base on request timestamp instead of prefine window slot
	eg: 2 request /min threshold
		request at 1:00:01 -> add to log -> log size = 1 < 2 -> allowed
		request at 1:00:30 -> add to log -> log size = 2 <=2 -> allowed
		request at 1:00:50 -> add to log -> log size = 3 > 2 -> drop
		request at 1:01:40 -> remove outdated time stamp not in rage [1:00:40 - 1:01:40] -> add to log -> log size = 2 <=2 -> allowed 
	- Pros:
		- Very accurate: windows is rolling hence never exceed the rate limit giving any time windows
	- Cons:
		- Complex
		- Require more memory to store all request timestamp despite allowed or rejected
		
 - *Sliding window counter*
	- Params: fix-sized time window, max request threshold, counter for current window, counter for previous window
	- Dynamic number of request formula = `request in current window + requests in previous window * overlap percentage of rolling window and previous window`
	- When a request comes -> calculate overlap% = `1 - (elapsed time into current window / window size)` -> estimated count = `(prev window counter * overlap%) + current window counter` -> if estimated count >= threshold then request is dropped -> else counter for current window is increased
	Pros:
		- Still memory efficient (2 counter per key instead of storing all timestamps)
		- Smooths the boundary spike problem of fixed window ~ Sliding window log without the memory cost
	Cons:
		- Approximation, not exect -> assume requests are evenly distributed within previous window
		- More compute per request -> slower
	eg: 2 request/min, windows at round minute boundaries:
		00:01:00 -> 00:02:00 (prev window) -> 2 request at 00:01:50 and 00:01:55 -> prev counter = 2
		00:02:00 -> 00:03:00 (current window) -> request comes at 00:02:05 -> elapsed into current window = 5s -> overlap% = 1 - (5/60) = 0.92 -> estimated count = `(2 * 0.92) + 0 = 1.84` < 2 threshold -> allow -> current counter = 1
		-> request comes at 00:02:10 -> elapsed into current window = 10s -> overlap% = 1 - (10/60) = 0.83 -> estimated count = `(2 * 0.83) + 1 = 2.67` > 2 threshold -> dropped -> current counter = 1
		-> request comes at 00:02:55 -> elapsed into current window = 55s -> overlap% = 1 - (55/60) = 0.08 -> estimated count = `(2 * 0.08) + 1 = 1.16` < 2 threshold -> allowed -> current counter = 2
		-> request comes at 00:02:59 -> elapsed into current window = 59s -> overlap% = 1 - (59/60) = 0.02 -> estimated count = `(2 * 0.02) + 2 = 2.04` > 2 threshold -> dropped -> current counter = 2

## High-level architecture

### Core idea
	- Use counter to keep track of the request from and identier like: IP, user_id
	- If counter > threshold -> drop request
	
Client send request to rate limter -> fetch counter from redis
	- If counter > limit -> request rejected
	- If counter < limit -> incease counter and save back to Redis -> allow request
	

	



