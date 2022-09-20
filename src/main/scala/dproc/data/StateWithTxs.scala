package dproc.data

import dproc.DProc.ST

/** Process state with transaction pool. */
final case class StateWithTxs[M, S, T](state: ST[M, S, T], txs: List[T])
