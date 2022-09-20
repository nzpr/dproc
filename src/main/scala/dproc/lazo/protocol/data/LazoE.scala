package dproc.lazo.protocol.data

/**
  * Data from execution engine. Everything that matters for consensus but can be adjusted programmatically.
  * State that really matters is a final state, so this data is read from the state of the final fringe
  * computed by the message.
  * @param bondsMap bonds map.
  * @param lazinessTolerance how many fringes behind current one dproc.node should keep to be able to validate messages
  *                          from senders that cannot keep up with the network.
  */
final case class LazoE[S](
    bonds: Bonds[S],
    lazinessTolerance: Int,
    ejectionThreshold: Int,
    expirationThreshold: Int
)
