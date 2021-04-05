# User Guide

Multithreaded simulation of a trading environment for 10 year swaps.
Orders are partially filled and the rest that are not are placed in the order book.
Logging happens on each thread and is written to a file.

Pricing by the clients are purposefully delayed via the simulation 
method after the pricer has updated. When the pricer updates, new
orders by a client have a mean around the new price. Once a client
has too many open trades, the client randomly closes one. Naturally,
the client's orders gravitate towards the new price.

Run the jar and follow instructions:

`java -jar simulation.jar`

## Known improvements to be made
- The entries at each price level in the order book are individual orders.
For example, a client could ask for a quantity of ten, and this would be stored
as ten objects. Instead there should be one object with a field describing the quantity.

- The order book currently has O(1) complexity for inserts and O(n) for deletions.
This should become O(log(n)) both ways with a heap/augmented tree
for example.

- The order book is a fixed sized array. When a certain threshold of price
is reached, an alert should be sent out to increase the size for example.
This would be acted on out of hours.

- Each simulated client has a position that doesn't make too much sense
(for example, they can send in a bid > ask) and this can be improved,
but at the exchange level there is no arbitrage.

- Each step size of the swap pricer is independent of current price.
The s.d. should be proportional to current price and have expectation 0
(as opposed to currently, with s.d. constant and expectation 0).

- There is code repetition in setting up the logger.
This should be abstracted out more.

- Use a unit testing library as opposed to the current testing approach.
