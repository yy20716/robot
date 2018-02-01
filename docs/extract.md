# Extract

The reuse of ontology terms creates links between data, making the ontology and the data more valuable. But often you want to reuse just a subset of terms from a target ontology, not the whole thing. Here we take the filtered ontology from the previous step and extract a "STAR" module for the term 'adrenal cortex' and its supporting terms:

    robot extract --method STAR \
        --input filtered.owl \
        --term-file uberon_module.txt \
        --output results/uberon_module.owl

See <a href="/examples/extract/uberon_module.txt" target="_blank">`uberon_module.txt`</a> for an example of a term file. Terms should be listed line by line, and comments can be included with `#`. Individual terms can be specified with `--term` followed by the CURIE.

The `--method` options fall into two groups: Minimal Information for Reuse of External Ontology Term (MIREOT) and Syntactic Locality Module Extractor (SLME).

- MIREOT: extract a simple hierarchy of terms
- STAR: use the SLME to extract a fixpoint-nested module
- TOP: use the SLME to extract a top module
- BOT: use the SLME to extract a bottom module

For MIREOT, both "upper" (ancestor) and "lower" (descendant) limits must be specified, like this:

    robot extract --method MIREOT \
        --input uberon_fragment.owl \
        --upper-term "obo:UBERON_0000465" \
        --lower-term "obo:UBERON_0001017" \
        --lower-term "obo:UBERON_0002369" \
        --output results/uberon_mireot.owl

To specify upper and lower term files, use `--upper-terms` and `--lower-terms`.

NOTE: The `extract` command works on the input ontology, not its imports. To extract from imports you should first [merge](/merge).

For more details see:

- <a href="http://dx.doi.org/10.3233/AO-2011-0087" target="_blank">MIREOT</a>
- <a href="http://owlcs.github.io/owlapi/apidocs_4/uk/ac/manchester/cs/owlapi/modularity/SyntacticLocalityModuleExtractor.html" target="_blank">SLME</a>
- <a href="http://owlcs.github.io/owlapi/apidocs_4/uk/ac/manchester/cs/owlapi/modularity/ModuleType.html" target="_blank">ModuleType</a>
