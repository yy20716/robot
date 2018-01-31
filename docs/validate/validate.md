# Validate

OWL 2 has a number of [profiles](https://www.w3.org/TR/owl2-profiles/) that strike different balances between expressive power and reasoning efficiency. ROBOT can validate an input ontology against a profile (EL, DL, RL, QL, and Full) and generate a report. For example:

    robot validate-profile --profile EL \
      --input merged.owl \
      --output results/merged-validation.txt