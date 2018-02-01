# Materialize

Robot can materialize all parent superclass and superclass expressions using the expression materializing reasoner, which wraps an existing OWL Reasoner

    robot materialize --reasoner ELK \
      --input emr_example.obo \
      --term obo:BFO_0000050  \
      --output results/emr_output.obo

This operation is similar to the reason command, but will also assert parents of the form `P some D`, for all P in the set passed in via `--term`

This can be [chained](/chaining) with [filter](/filter) and [reduce](/reduce) to create a complete ontology subset:

    robot materialize --reasoner ELK \
      --input emr_example.obo \
      --term BFO:0000050 \
      filter -t BFO:0000050 \
      reduce --output results/emr_reduced.obo