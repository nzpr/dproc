package dproc

class MPropSpec {

  // Proposer should expand the view with attestations added during message creation

  // Proposer should not expand the view with state transitions added during message creation

  // mixed scenario - attestations and state transitions. Expansion should be done correctly.
  // Only attestations with no state transition in the view that are not seen by initial state should be included.

}
