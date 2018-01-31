# Reduce

ROBOT can be used to remove redundant subClassOf axioms:

    robot reduce --reasoner ELK \
      --input ribosome.owl \
      --output results/reduced.owl

See <a href="/reason/reason">reason</a> for details on supported reasoners (currently JFact and EMR are not supported in `reduce`).