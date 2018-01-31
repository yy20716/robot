# Reason

One of the main benefits of working with OWL is the availability of powerful automated reasoners. There are several reasoners available, and each has different capabilities and characteristics. For this example we'll be using <a href="https://code.google.com/p/elk-reasoner/" target="_blank">ELK</a>, a very fast reasoner that supports the EL subset of OWL 2.

    robot reason --reasoner ELK \
      --input ribosome.owl \
      --output results/reasoned.owl

It's also possible to place the new inferences in their own ontology:

    robot reason --reasoner ELK \
      --create-new-ontology true \
      --input ribosome.owl \
      --output results/new_axioms.owl

If no `--reasoner` is provided, ROBOT will default to ELK. The following other reasoner options are supported:
  
  * `hermit` - <a href="http://www.hermit-reasoner.com/" target="_blank">HermiT</a>
  * `jfact` - <a href="http://jfact.sourceforge.net/" target="_blank">JFact</a>
  * `emr` - <a href="http://static.javadoc.io/org.geneontology/expression-materializing-reasoner/0.1.3/org/geneontology/reasoner/ExpressionMaterializingReasoner.html" target="_blank">Expression Materializing Reasoner</a>
  * `structural` - <a href="http://owlcs.github.io/owlapi/apidocs_4/org/semanticweb/owlapi/reasoner/structural/StructuralReasoner.html" target="_blank">Structural Reasoner</a>