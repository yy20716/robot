package org.obolibrary.robot;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.manchestersyntax.parser.ManchesterOWLSyntaxClassExpressionParser;
import org.semanticweb.owlapi.manchestersyntax.renderer.ParserException;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.util.SimpleShortFormProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.manchester.cs.owl.owlapi.OWLDataFactoryImpl;

/**
 * Generate OWL from tables. Based on "Overcoming the ontology enrichment bottleneck with Quick Term
 * Templates" (<a href="http://dx.doi.org/10.3233/AO-2011-0086">link</a>). See template.md for
 * details.
 *
 * @author <a href="mailto:james@overton.ca">James A. Overton</a>
 */
public class TemplateOperation {
  /** Logger. */
  private static final Logger logger = LoggerFactory.getLogger(TemplateOperation.class);

  /** OWL Namespace. */
  private static String owl = "http://www.w3.org/2002/07/owl#";

  /** Shared OWLOntologyManager. */
  private static OWLOntologyManager outputManager = OWLManager.createOWLOntologyManager();

  /** Shared DataFactory. */
  private static OWLDataFactory dataFactory = new OWLDataFactoryImpl();

  /**
   * Error message when an axiom annotation does not have the appropriate annotation or class
   * expression to the left. Expects: table name, row number, row id, column number, column name,
   * cell value, axiom type.
   */
  private static String axiomAnnotationError =
      "Axiom annotation \"%6$s\" at row %2$d (\"%3$s\"), "
          + "column %4$d (\"%5$s\") in table \"%1$s\" "
          + "requires %7$s in the previous column.";

  /**
   * Error message when the number of header columns does not match the number of template columns.
   * Expects: table name, header count, template count.
   */
  private static String columnMismatchError =
      "The number of header columns (%2$d) must match "
          + "the number of template columns (%3$d) "
          + "in table \"%1$s\".";

  /**
   * Error message when a template cannot be understood. Expects: table name, column number, column
   * name, template.
   */
  private static String unknownTemplateError =
      "Could not interpret template string \"%4$s\" "
          + "for column %2$d (\"%3$s\")."
          + "in table \"%1$s\".";

  /**
   * Error message when content cannot be parsed. Expects: table name, row number, row id, columns
   * number, column name, content, message.
   */
  private static String parseError =
      "Error while parsing \"%6$s\" "
          + "at row %2$d (\"%3$s\"), "
          + "column %4$d (\"%5$s\") "
          + "in table \"%1$s\": "
          + "%7$s";

  /**
   * Error message when we cannot create an IRI for a row ID. Expects: table name, row number, row
   * id.
   */
  private static String nullIDError =
      "Could not create IRI for ID \"%3$s\" "
          + "with label \"%4$s\" "
          + "at row %2$d "
          + "in table \"%1$s\".";

  /**
   * Error message when a row for a class does not have a type. Should be "subclass" or
   * "equivalent". Expects: table name, row number, row id.
   */
  private static String missingTypeError =
      "No class type found for row %2$d (\"%3$s\") " + "in table \"%1$s\".";

  /**
   * Error message when a class type is not recognized. Should be "subclass" or "equivalent".
   * Expects: table name, row number, row id, value.
   */
  private static String unknownTypeError =
      "Unknown class type \"%4$s\" " + "for row %2$d (\"%3$s\") " + "in table \"%1$s\".";

  /**
   * Find an annotation property with the given name or create one.
   *
   * @param checker used to search by rdfs:label (for example)
   * @param name the name to search for
   * @return an annotation property
   * @throws Exception if the name cannot be resolved
   */
  public static OWLAnnotationProperty getAnnotationProperty(
      QuotedEntityChecker checker, String name) throws Exception {
    OWLAnnotationProperty property = checker.getOWLAnnotationProperty(name, true);
    if (property != null) {
      return property;
    }
    throw new Exception("Could not find annotation property: " + name);
  }

  /**
   * Find a datatype with the given name or create one.
   *
   * @param checker used to search by rdfs:label (for example)
   * @param name the name to search for
   * @return a datatype
   * @throws Exception if the name cannot be resolved
   */
  public static OWLDatatype getDatatype(QuotedEntityChecker checker, String name) throws Exception {
    OWLDatatype datatype = checker.getOWLDatatype(name, true);
    if (datatype != null) {
      return datatype;
    }
    throw new Exception("Could not find datatype: " + name);
  }

  /**
   * Return a string annotation for the given template string and value.
   *
   * @param checker used to resolve the annotation property
   * @param template the template string
   * @param value the value for the annotation
   * @return a new annotation with property and string literal value
   * @throws Exception if the annotation property cannot be found
   */
  public static OWLAnnotation getStringAnnotation(
      QuotedEntityChecker checker, String template, String value) throws Exception {
    String name = template.substring(1).trim();
    OWLAnnotationProperty property = getAnnotationProperty(checker, name);
    return dataFactory.getOWLAnnotation(property, dataFactory.getOWLLiteral(value));
  }

  /**
   * Return a typed annotation for the given template string and value. The template string format
   * is "AT [name]^^[datatype]" and the value is any string.
   *
   * @param checker used to resolve the annotation property and datatype
   * @param template the template string
   * @param value the value for the annotation
   * @return a new annotation axiom with property and typed literal value
   * @throws Exception if the annotation property cannot be found
   */
  public static OWLAnnotation getTypedAnnotation(
      QuotedEntityChecker checker, String template, String value) throws Exception {
    template = template.substring(2).trim();
    String name = template.substring(0, template.indexOf("^^")).trim();
    String typeName = template.substring(template.indexOf("^^") + 2, template.length()).trim();
    OWLAnnotationProperty property = getAnnotationProperty(checker, name);
    OWLDatatype datatype = getDatatype(checker, typeName);
    return dataFactory.getOWLAnnotation(property, dataFactory.getOWLLiteral(value, datatype));
  }

  /**
   * Return a language tagged annotation for the given template and value. The template string
   * format is "AL [name]@[lang]" and the value is any string.
   *
   * @param checker used to resolve the annotation property
   * @param template the template string
   * @param value the value for the annotation
   * @return a new annotation axiom with property and language tagged literal
   * @throws Exception if the annotation property cannot be found
   */
  public static OWLAnnotation getLanguageAnnotation(
      QuotedEntityChecker checker, String template, String value) throws Exception {
    template = template.substring(2).trim();
    String name = template.substring(0, template.indexOf("@")).trim();
    String lang = template.substring(template.indexOf("@") + 1, template.length()).trim();
    OWLAnnotationProperty property = getAnnotationProperty(checker, name);
    return dataFactory.getOWLAnnotation(property, dataFactory.getOWLLiteral(value, lang));
  }

  /**
   * Return an IRI annotation for the given template string and value. The template string format is
   * "AI [name]" and the value is a string that can be interpreted as an IRI.
   *
   * @param checker used to resolve the annotation property
   * @param template the template string
   * @param value the IRI value for the annotation
   * @return a new annotation axiom with property and an IRI value
   * @throws Exception if the annotation property cannot be found
   */
  public static OWLAnnotation getIRIAnnotation(
      QuotedEntityChecker checker, String template, IRI value) throws Exception {
    String name = template.substring(2).trim();
    OWLAnnotationProperty property = getAnnotationProperty(checker, name);
    return dataFactory.getOWLAnnotation(property, value);
  }

  /**
   * Return true if the template string is valid, false otherwise.
   *
   * @param template the template string to check
   * @return true if valid, false otherwise
   */
  public static boolean validateTemplateString(String template) {
    template = template.trim();
    if (template.equals("ID")) {
      return true;
    }
    if (template.equals("LABEL")) {
      return true;
    }
    if (template.equals("TYPE")) {
      return true;
    }
    if (template.equals("CLASS_TYPE")) {
      return true;
    }
    if (template.matches("^(>?C|>{0,2}A[L,T,I]?) .*")) {
      return true;
    }
    if (template.equals("CI")) {
      return true;
    }
    return false;
  }

  /**
   * Use a table to generate an ontology. With this signature we use all defaults.
   *
   * @param tables a map from names to tables of data
   * @return a new ontology generated from the table
   * @throws Exception when names or templates cannot be handled
   */
  public static OWLOntology template(Map<String, List<List<String>>> tables) throws Exception {
    return template(tables, null, null, null);
  }

  /**
   * Use tables to generate an ontology.
   *
   * @param tables a map from names to tables of data
   * @param inputOntology the ontology to use to resolve names
   * @return a new ontology generated from the table
   * @throws Exception when names or templates cannot be handled
   */
  public static OWLOntology template(
      Map<String, List<List<String>>> tables, OWLOntology inputOntology) throws Exception {
    return template(tables, inputOntology, null, null);
  }

  /**
   * Use tables to generate an ontology.
   *
   * @param tables a map from names to tables of data
   * @param inputOntology the ontology to use to resolve names
   * @param checker used to find entities by name
   * @return a new ontology generated from the table
   * @throws Exception when names or templates cannot be handled
   */
  public static OWLOntology template(
      Map<String, List<List<String>>> tables,
      OWLOntology inputOntology,
      QuotedEntityChecker checker)
      throws Exception {
    return template(tables, inputOntology, checker, null);
  }

  /**
   * Use tables to generate an ontology.
   *
   * @param tables a map from names to tables of data
   * @param inputOntology the ontology to use to resolve names
   * @param ioHelper used to find entities by name
   * @return a new ontology generated from the table
   * @throws Exception when names or templates cannot be handled
   */
  public static OWLOntology template(
      Map<String, List<List<String>>> tables, OWLOntology inputOntology, IOHelper ioHelper)
      throws Exception {
    return template(tables, inputOntology, null, ioHelper);
  }

  /**
   * Use tables to generate an ontology. Input is a map from table names to tables. The first row of
   * each table must be header names. The second row of each table must be template strings,
   * including ID and CLASS_TYPE columns. The new ontology is created in two passes: first terms are
   * declared and annotations added, then logical axioms are added. This allows annotations from the
   * first pass to be used as names in the logical axioms.
   *
   * @param tables a map from names to tables of data
   * @param inputOntology the ontology to use to resolve names
   * @param checker used to find entities by name
   * @param ioHelper used to find entities by name
   * @return a new ontology generated from the table
   * @throws Exception when names or templates cannot be handled
   */
  public static OWLOntology template(
      Map<String, List<List<String>>> tables,
      OWLOntology inputOntology,
      QuotedEntityChecker checker,
      IOHelper ioHelper)
      throws Exception {
    logger.debug("Templating...");

    // Check templates and find the ID and LABEL columns.
    Map<String, Integer> idColumns = new HashMap<String, Integer>();
    Map<String, Integer> labelColumns = new HashMap<String, Integer>();
    for (Map.Entry<String, List<List<String>>> table : tables.entrySet()) {
      String tableName = table.getKey();
      List<List<String>> rows = table.getValue();

      List<String> headers = rows.get(0);
      List<String> templates = rows.get(1);
      if (headers.size() != templates.size()) {
        throw new Exception(
            String.format(columnMismatchError, tableName, headers.size(), templates.size()));
      }

      Integer idColumn = -1;
      Integer labelColumn = -1;
      for (int column = 0; column < templates.size(); column++) {
        String template = templates.get(column);
        if (template == null) {
          continue;
        }
        template = template.trim();
        if (template.isEmpty()) {
          continue;
        }
        if (!validateTemplateString(template)) {
          throw new Exception(
              String.format(
                  unknownTemplateError, tableName, column + 1, headers.get(column), template));
        }
        if (template.equals("ID")) {
          idColumn = column;
        }
        if (template.equals("LABEL")) {
          labelColumn = column;
        }
      }

      if (idColumn == -1 && labelColumn == -1) {
        throw new Exception(
            "Template row must include an \"ID\" or \"LABEL\" column in table: " + tableName);
      }
      idColumns.put(tableName, idColumn);
      labelColumns.put(tableName, labelColumn);
    }

    OWLOntology outputOntology = outputManager.createOntology();

    if (ioHelper == null) {
      ioHelper = new IOHelper();
    }
    if (checker == null) {
      checker = new QuotedEntityChecker();
      checker.setIOHelper(ioHelper);
      checker.addProvider(new SimpleShortFormProvider());
      checker.addProperty(dataFactory.getRDFSLabel());
    }
    if (inputOntology != null) {
      checker.addAll(inputOntology);
    }

    // Process the table in two passes.
    // The first pass adds declarations and annotations to the ontology,
    // then adds the term to the EntityChecker so it can be used
    // by the parser for logical definitions.
    for (String tableName : tables.keySet()) {
      int idColumn = idColumns.get(tableName);
      int labelColumn = labelColumns.get(tableName);
      List<List<String>> rows = tables.get(tableName);
      for (int row = 2; row < rows.size(); row++) {
        addAnnotations(outputOntology, tableName, rows, row, idColumn, labelColumn, checker);
      }
    }

    // Second pass: add logic to existing entities.
    ManchesterOWLSyntaxClassExpressionParser parser =
        new ManchesterOWLSyntaxClassExpressionParser(dataFactory, checker);
    for (String tableName : tables.keySet()) {
      int idColumn = idColumns.get(tableName);
      int labelColumn = labelColumns.get(tableName);
      List<List<String>> rows = tables.get(tableName);
      for (int row = 2; row < rows.size(); row++) {
        addLogic(outputOntology, tableName, rows, row, idColumn, labelColumn, checker, parser);
      }
    }

    return outputOntology;
  }

  /**
   * Use type, id, and label information to get an entity from the data in a row. Requires either:
   * an id (default type is owl:Class); an id and type; or a label.
   *
   * @param checker for looking up labels
   * @param type the IRI of the type for this entity, or null
   * @param id the ID for this entity, or null
   * @param label the label for this entity, or null
   * @return the entity
   * @throws Exception if the entity cannot be created
   */
  public static OWLEntity getEntity(QuotedEntityChecker checker, IRI type, String id, String label)
      throws Exception {

    IOHelper ioHelper = checker.getIOHelper();

    if (id != null && ioHelper != null) {
      IRI iri = ioHelper.createIRI(id);
      if (type == null) {
        type = IRI.create(owl + "Class");
      }
      String t = type.toString();
      if (t.equals(owl + "Class")) {
        return dataFactory.getOWLClass(iri);
      } else if (t.equals(owl + "AnnotationProperty")) {
        return dataFactory.getOWLAnnotationProperty(iri);
      } else if (t.equals(owl + "ObjectProperty")) {
        return dataFactory.getOWLObjectProperty(iri);
      } else if (t.equals(owl + "DatatypeProperty")) {
        return dataFactory.getOWLDataProperty(iri);
      } else if (t.equals(owl + "Datatype")) {
        return dataFactory.getOWLDatatype(iri);
      } else {
        return dataFactory.getOWLNamedIndividual(iri);
      }
    }

    if (label != null && type != null) {
      String t = type.toString();
      if (t.equals(owl + "Class")) {
        return checker.getOWLClass(label);
      } else if (t.equals(owl + "AnnotationProperty")) {
        return checker.getOWLAnnotationProperty(label);
      } else if (t.equals(owl + "ObjectProperty")) {
        return checker.getOWLObjectProperty(label);
      } else if (t.equals(owl + "DatatypeProperty")) {
        return checker.getOWLDataProperty(label);
      } else if (t.equals(owl + "Datatype")) {
        return checker.getOWLDatatype(label);
      } else {
        return checker.getOWLIndividual(label);
      }
    }

    if (label != null) {
      return checker.getOWLEntity(label);
    }

    return null;
  }

  /**
   * Get a list of the IRIs defined in a set of template tables.
   *
   * @param tables a map from table names to tables
   * @param ioHelper used to find entities by name
   * @return a list of IRIs
   * @throws Exception when names or templates cannot be handled
   */
  public static List<IRI> getIRIs(Map<String, List<List<String>>> tables, IOHelper ioHelper)
      throws Exception {
    List<IRI> iris = new ArrayList<IRI>();
    for (Map.Entry<String, List<List<String>>> table : tables.entrySet()) {
      String tableName = table.getKey();
      List<List<String>> rows = table.getValue();
      iris.addAll(getIRIs(tableName, rows, ioHelper));
    }
    return iris;
  }

  /**
   * Get a list of the IRIs defined in a template table.
   *
   * @param tableName the name of the table
   * @param rows the table of data
   * @param ioHelper used to find entities by name
   * @return a list of IRIs
   * @throws Exception when names or templates cannot be handled
   */
  public static List<IRI> getIRIs(String tableName, List<List<String>> rows, IOHelper ioHelper)
      throws Exception {
    // Find the ID column.
    List<String> templates = rows.get(1);
    int idColumn = -1;
    for (int column = 0; column < templates.size(); column++) {
      String template = templates.get(column);
      if (template == null) {
        continue;
      }
      template = template.trim();
      if (template.equals("ID")) {
        idColumn = column;
      }
    }
    if (idColumn == -1) {
      throw new Exception("Template row must include an \"ID\" column " + "in table " + tableName);
    }

    List<IRI> iris = new ArrayList<IRI>();
    for (int row = 2; row < rows.size(); row++) {
      String id = null;
      try {
        id = rows.get(row).get(idColumn);
      } catch (IndexOutOfBoundsException e) {
        continue;
      }
      if (id == null || id.trim().isEmpty()) {
        continue;
      }
      IRI iri = ioHelper.createIRI(id);
      if (iri == null) {
        continue;
      }
      iris.add(iri);
    }

    return iris;
  }

  /**
   * Use templates to add entities and their annotations to an ontology.
   *
   * @param outputOntology the ontology to add axioms to
   * @param tableName the name of the current table
   * @param rows the table to use
   * @param row the current row to use
   * @param idColumn the column that holds the ID for the entity
   * @param labelColumn the column that holds the LABEL for the entity
   * @param checker used to find annotation properties by name
   * @throws Exception when names or templates cannot be handled
   */
  private static void addAnnotations(
      OWLOntology outputOntology,
      String tableName,
      List<List<String>> rows,
      int row,
      Integer idColumn,
      Integer labelColumn,
      QuotedEntityChecker checker)
      throws Exception {
    List<String> headers = rows.get(0);
    List<String> templates = rows.get(1);

    String label = null;
    String id = null;
    if (idColumn != null && idColumn != -1) {
      try {
        id = rows.get(row).get(idColumn);
      } catch (IndexOutOfBoundsException e) {
        return;
      }
      if (id == null || id.trim().isEmpty()) {
        return;
      }
    }

    IOHelper ioHelper = checker.getIOHelper();
    IRI type = null;
    Set<OWLAnnotation> annotations = new HashSet<>();
    // For handling nested axioms (annotation on annotation on annotation)
    // Key is first level, value is a map with key as second level annotation
    // and value as set of third-level annotations (or empty set if none)
    Map<OWLAnnotation, Map<OWLAnnotation, Set<OWLAnnotation>>> nested = new HashMap<>();
    // Track the last top-level annotation
    OWLAnnotation lastAnnotation = null;
    // Track the last second-level annotation
    // This is reset to null each time we return to a top-level annotation
    OWLAnnotation lastAxiomAnnotation = null;
    Map<OWLAnnotation, Set<OWLAnnotation>> axiomAnnotations;
    Set<OWLAnnotation> axiomAnnotationAnnotations;

    // For each column, apply templates for annotations.
    for (int column = 0; column < headers.size(); column++) {
      String template = templates.get(column);
      if (template == null) {
        continue;
      }
      template = template.trim();
      if (template.isEmpty()) {
        continue;
      }

      String cell = null;
      try {
        cell = rows.get(row).get(column);
      } catch (IndexOutOfBoundsException e) {
        continue;
      }

      if (cell == null) {
        continue;
      }
      if (cell.trim().isEmpty()) {
        continue;
      }

      // If the template contains SPLIT=X,
      // then split the cell value
      // and remove that string from the template.
      List<String> values = new ArrayList<String>();
      Pattern splitter = Pattern.compile("SPLIT=(\\S+)");
      Matcher matcher = splitter.matcher(template);
      if (matcher.find()) {
        Pattern split = Pattern.compile(Pattern.quote(matcher.group(1)));
        values = new ArrayList<String>(Arrays.asList(split.split(cell)));
        template = matcher.replaceAll("").trim();
      } else {
        values.add(cell);
      }

      // Create OWLAnnotation objects
      // Link any axiom annotations in a map
      for (String value : values) {
        if (template.equals("LABEL")) {
          label = value;
          lastAxiomAnnotation = null;
          lastAnnotation = getStringAnnotation(checker, "A rdfs:label", value);
          annotations.add(lastAnnotation);
        } else if (template.equals("TYPE")) {
          OWLEntity entity = checker.getOWLEntity(value);
          if (entity != null) {
            type = entity.getIRI();
          } else {
            type = ioHelper.createIRI(value);
          }
          OWLAnnotationProperty rdfType = getAnnotationProperty(checker, "rdf:type");
          annotations.add(dataFactory.getOWLAnnotation(rdfType, type));
        } else if (template.startsWith("A")) {
          lastAxiomAnnotation = null;
          lastAnnotation = getAnnotation(checker, ioHelper, template, value);
          annotations.add(lastAnnotation);
        } else if (template.startsWith(">A")) {
          if (lastAnnotation == null) {
            // Just in case an AA template is first
            throw new Exception(
                String.format(
                    axiomAnnotationError,
                    tableName,
                    row + 1,
                    id,
                    column + 1,
                    template,
                    value,
                    "an annotation"));
          }
          // Get annotation based on annotation type
          lastAxiomAnnotation = getAnnotation(checker, ioHelper, template.substring(1), value);
          // If the last annotation is already in the map, get it's existing annotations
          if (nested.containsKey(lastAnnotation)) {
            axiomAnnotations = nested.get(lastAnnotation);
          } else {
            axiomAnnotations = new HashMap<>();
          }
          // Add this annotation to map of axiom annotations with an empty set
          axiomAnnotations.put(lastAxiomAnnotation, Sets.newHashSet());
          nested.put(lastAnnotation, axiomAnnotations);
          // Remove from annotation set to prevent duplication
          if (annotations.contains(lastAnnotation)) {
            annotations.remove(lastAnnotation);
          }
          // Handle axiom annotation annotations ?
        } else if (template.startsWith(">>A")) {
          if (lastAxiomAnnotation == null) {
            // The last axiom annotation is reset each time we go back to a top-level annotation.
            // If there isn't an annotation with the AA template string before the AAA template,
            // there is nothing to annotate...
            throw new Exception(
                String.format(
                    axiomAnnotationError,
                    tableName,
                    row + 1,
                    id,
                    column + 1,
                    template,
                    value,
                    "an axiom annotation"));
          }
          // These are put in during the >A loop, so they should always be there
          axiomAnnotations = nested.get(lastAnnotation);
          axiomAnnotationAnnotations = axiomAnnotations.get(lastAxiomAnnotation);
          // Add this iteration of annotation and put into the nested map
          axiomAnnotationAnnotations.add(
              getAnnotation(checker, ioHelper, template.substring(2), value));
          axiomAnnotations.put(lastAxiomAnnotation, axiomAnnotationAnnotations);
          nested.put(lastAnnotation, axiomAnnotations);
        }
      }
    }

    OWLEntity entity = getEntity(checker, type, id, label);
    if (entity == null) {
      throw new Exception(String.format(nullIDError, tableName, row + 1, id, label));
    }
    // Create axioms from OWLAnnotation sets
    Set<OWLAnnotationAssertionAxiom> axioms = getAnnotationAxioms(entity, annotations, nested);
    // Add annotations to an entity
    OWLOntology ontology = outputManager.createOntology();
    outputManager.addAxiom(ontology, dataFactory.getOWLDeclarationAxiom(entity));
    outputManager.addAxioms(ontology, axioms);

    checker.addAll(ontology);
    MergeOperation.mergeInto(ontology, outputOntology);
  }

  /**
   * Use templates to add logical axioms to an ontology.
   *
   * @param ontology the ontology to add axioms to
   * @param tableName the name of the current table
   * @param rows the table to use
   * @param row the current row to use
   * @param idColumn the column that holds the ID for the entity
   * @param labelColumn the column that holds the LABEL for the entity
   * @param checker used to find annotation properties by name
   * @param parser used parse expressions
   * @throws Exception when names or templates cannot be handled
   */
  private static void addLogic(
      OWLOntology ontology,
      String tableName,
      List<List<String>> rows,
      int row,
      Integer idColumn,
      Integer labelColumn,
      QuotedEntityChecker checker,
      ManchesterOWLSyntaxClassExpressionParser parser)
      throws Exception {
    List<String> headers = rows.get(0);
    List<String> templates = rows.get(1);

    IOHelper ioHelper = checker.getIOHelper();

    String id = null;
    if (idColumn != null && idColumn != -1) {
      try {
        id = rows.get(row).get(idColumn);
      } catch (IndexOutOfBoundsException e) {
        return;
      }
      if (id == null || id.trim().isEmpty()) {
        return;
      }
    }

    String label = null;
    if (labelColumn != null && labelColumn != -1) {
      try {
        label = rows.get(row).get(labelColumn);
      } catch (IndexOutOfBoundsException e) {
        return;
      }
      if (label == null || label.trim().isEmpty()) {
        return;
      }
    }

    String classType = "subclass";
    Set<OWLClassExpression> classExpressions = new HashSet<>();
    OWLClassExpression lastExpression = null;
    Map<OWLClassExpression, Set<OWLAnnotation>> annotatedExpressions = new HashMap<>();

    // For each column, add logical axioms.
    for (int column = 0; column < headers.size(); column++) {
      String template = templates.get(column);
      if (template == null) {
        continue;
      }
      template = template.trim();
      if (template.isEmpty()) {
        continue;
      }

      String header = headers.get(column);
      String cell = null;
      try {
        cell = rows.get(row).get(column);
      } catch (IndexOutOfBoundsException e) {
        continue;
      }

      if (cell == null) {
        continue;
      }
      if (cell.trim().isEmpty()) {
        continue;
      }
      String content = QuotedEntityChecker.wrap(cell);

      if (template.equals("CLASS_TYPE")) {
        classType = cell.trim().toLowerCase();
      } else if (template.startsWith("C ")) {
        String sub = template.substring(2).trim().replaceAll("%", content);
        try {
          lastExpression = parser.parse(sub);
        } catch (ParserException e) {
          throw new Exception(
              String.format(
                  parseError, tableName, row + 1, id, column + 1, header, sub, e.getMessage()));
        }
        classExpressions.add(lastExpression);
      } else if (template.startsWith("CI")) {
        IRI iri = ioHelper.createIRI(cell);
        lastExpression = dataFactory.getOWLClass(iri);
        classExpressions.add(lastExpression);
      } else if (template.startsWith(">C ")) {
        if (lastExpression == null) {
          throw new Exception(
              String.format(
                  axiomAnnotationError,
                  tableName,
                  row + 1,
                  id,
                  column + 1,
                  template,
                  cell,
                  "a class expression"));
        }
        Set<OWLAnnotation> annotations;
        if (annotatedExpressions.containsKey(lastExpression)) {
          annotations = annotatedExpressions.get(lastExpression);
        } else {
          annotations = new HashSet<>();
        }
        annotations.add(getStringAnnotation(checker, template.substring(1).trim(), cell));
        annotatedExpressions.put(lastExpression, annotations);
        // Remove to prevent duplication
        if (classExpressions.contains(lastExpression)) {
          classExpressions.remove(lastExpression);
        }
      }
    }

    // Make sure there are expressions to add
    if ((classExpressions.size() == 0) && (annotatedExpressions.isEmpty())) {
      return;
    }
    if (classType == null) {
      throw new Exception(String.format(missingTypeError, tableName, row + 1, id));
    }
    classType = classType.trim().toLowerCase();

    // Now validate and build the class.
    OWLClass cls = null;
    if (id != null) {
      IRI iri = ioHelper.createIRI(id);
      cls = dataFactory.getOWLClass(iri);
    } else if (label != null) {
      cls = checker.getOWLClass(label);
    }
    if (cls == null) {
      throw new Exception(String.format(nullIDError, tableName, row + 1, id, label));
    }
    Set<OWLAxiom> axioms = getLogicalAxioms(cls, classType, classExpressions, annotatedExpressions);
    if (axioms == null) {
      throw new Exception(String.format(unknownTypeError, tableName, row + 1, id));
    } else {
      ontology.getOWLOntologyManager().addAxioms(ontology, axioms);
    }
  }

  /**
   * Create an OWLAnnotation based on the template string and cell value.
   *
   * @param checker used to resolve the annotation property
   * @param ioHelper IOHelper used to create IRIs from values
   * @param template the template string
   * @param value the value for the annotation
   * @return OWLAnnotation, or null if template string is not supported
   * @throws Exception if annotation property cannot be found
   */
  private static OWLAnnotation getAnnotation(
      QuotedEntityChecker checker, IOHelper ioHelper, String template, String value)
      throws Exception {
    if (template.startsWith("A ")) {
      return getStringAnnotation(checker, template, value);
    } else if (template.startsWith("AT ") && template.indexOf("^^") > -1) {
      return getTypedAnnotation(checker, template, value);
    } else if (template.startsWith("AL ") && template.indexOf("@") > -1) {
      return getLanguageAnnotation(checker, template, value);
    } else if (template.startsWith("AI ")) {
      IRI iri = ioHelper.createIRI(value);
      return getIRIAnnotation(checker, template, iri);
    } else {
      return null;
    }
  }

  /**
   * Get a set of annotation axioms for an OWLEntity. Supports axiom annotations and axiom
   * annotation annotations.
   *
   * @param entity OWLEntity to annotation
   * @param annotations Set of OWLAnnotations
   * @param nested Map with top-level OWLAnnotation as key and another map (axiom OWLAnnotation, set
   *     of axiom annotation OWLAnnotations) as value
   * @return Set of OWLAnnotationAssertionAxioms
   */
  private static Set<OWLAnnotationAssertionAxiom> getAnnotationAxioms(
      OWLEntity entity,
      Set<OWLAnnotation> annotations,
      Map<OWLAnnotation, Map<OWLAnnotation, Set<OWLAnnotation>>> nested) {
    Set<OWLAnnotationAssertionAxiom> axioms = new HashSet<>();
    // Create basic annotations
    for (OWLAnnotation annotation : annotations) {
      axioms.add(dataFactory.getOWLAnnotationAssertionAxiom(entity.getIRI(), annotation));
    }
    // Create annotations with axiom annotations
    for (Entry<OWLAnnotation, Map<OWLAnnotation, Set<OWLAnnotation>>> annotationPlusAxioms :
        nested.entrySet()) {
      OWLAnnotation annotation = annotationPlusAxioms.getKey();
      Set<OWLAnnotation> axiomAnnotations = new HashSet<>();
      // For each annotation with its axiom annotations ...
      for (Entry<OWLAnnotation, Set<OWLAnnotation>> nestedSet :
          annotationPlusAxioms.getValue().entrySet()) {
        OWLAnnotation axiomAnnotation = nestedSet.getKey();
        // Check if there are annotations on the axiom annotations, and add those
        Set<OWLAnnotation> axiomAnnotationAnnotations = nestedSet.getValue();
        if (axiomAnnotationAnnotations.isEmpty()) {
          axiomAnnotations.add(axiomAnnotation);
        } else {
          OWLAnnotationProperty property = axiomAnnotation.getProperty();
          OWLAnnotationValue value = axiomAnnotation.getValue();
          axiomAnnotations.add(
              dataFactory.getOWLAnnotation(property, value, axiomAnnotationAnnotations));
        }
      }
      axioms.add(
          dataFactory.getOWLAnnotationAssertionAxiom(
              entity.getIRI(), annotation, axiomAnnotations));
    }
    return axioms;
  }

  /**
   * Get a set of OWLAxioms (subclass or equivalent) for an OWLClass. Supports axiom annotations.
   *
   * @param cls OWLClass to add axioms to
   * @param classType subclass or equivalent
   * @param classExpressions Set of OWLClassExpressions
   * @param annotatedExpressions Map of annotated OWLClassExpressions and the Set of OWLAnnotations
   * @return Set of OWLAxioms, or null if classType is not subclass or equivalent
   */
  private static Set<OWLAxiom> getLogicalAxioms(
      OWLClass cls,
      String classType,
      Set<OWLClassExpression> classExpressions,
      Map<OWLClassExpression, Set<OWLAnnotation>> annotatedExpressions) {
    Set<OWLAxiom> axioms = new HashSet<>();
    if (classType.equals("subclass")) {
      for (OWLClassExpression expression : classExpressions) {
        axioms.add(dataFactory.getOWLSubClassOfAxiom(cls, expression));
      }
      for (Entry<OWLClassExpression, Set<OWLAnnotation>> annotatedEx :
          annotatedExpressions.entrySet()) {
        axioms.add(
            dataFactory.getOWLSubClassOfAxiom(cls, annotatedEx.getKey(), annotatedEx.getValue()));
      }
      return axioms;
    } else if (classType.equals("equivalent")) {
      // Since it's an intersection, all annotations will be added to the same axiom
      Set<OWLAnnotation> annotations = new HashSet<>();
      for (Entry<OWLClassExpression, Set<OWLAnnotation>> annotatedEx :
          annotatedExpressions.entrySet()) {
        classExpressions.add(annotatedEx.getKey());
        annotations.addAll(annotatedEx.getValue());
      }
      OWLObjectIntersectionOf intersection =
          dataFactory.getOWLObjectIntersectionOf(classExpressions);
      OWLAxiom axiom;
      if (!annotations.isEmpty()) {
        axiom = dataFactory.getOWLEquivalentClassesAxiom(cls, intersection, annotations);
      } else {
        axiom = dataFactory.getOWLEquivalentClassesAxiom(cls, intersection);
      }
      return Sets.newHashSet(axiom);
    } else {
      return null;
    }
  }
}
