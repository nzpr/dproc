package dproc.data

import dproc.lazo.protocol.data.LazoE

final case class ExeEngine[F[_], M, S, T](
    computeLazoData: Set[M] => F[LazoE[S]], // read data required for Lazo from the fringe (or a message if supported)
    validate: M => F[Boolean],              // validation specific to execution engine
    conflicts: (T, T) => F[Boolean],        // compute for predicate of conflict
    depends: (T, T) => F[Boolean]           // compute for predicate of dependency
)
