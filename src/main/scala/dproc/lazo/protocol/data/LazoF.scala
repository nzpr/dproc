package dproc.lazo.protocol.data

/**
  * Finality data.
  * @param fFringe final fringe.
  * @param pFringe partition fringe.
  */
final case class LazoF[M](fFringe: Set[M], pFringe: Set[M])
