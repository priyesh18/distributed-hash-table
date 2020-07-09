## A distributed hash table based on the Chord Protocol.
1. Each android app instance(total 5) has an activity and a content provider.
2. The content provider instances implement the DHT functionality using Java Sockets.
3. The DHT supports insert and query operations according to the Chord protocol.
4. The code handles:
    - ID space partitioning/re-partioning
    - Ring-based routing
    - Node joins(upto 5).

Reference: [Chord Protocol Paper](http://www.cse.buffalo.edu/~stevko/courses/cse486/spring19/files/chord_sigcomm.pdf)  
Project is part of [CSE 586 - Distributed Systems by Professor Steve Ko](https://cse.buffalo.edu/~stevko/courses/cse486/spring20/index.html).