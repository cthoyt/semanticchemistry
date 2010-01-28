/* Copyright (C) 2010  Egon Willighagen <egonw@users.sf.net>
 */
package com.google.code.semanticchemistry.cdk.io;

import java.util.Iterator;
import java.util.Map;

import org.openscience.cdk.interfaces.IChemObject;
import org.openscience.cdk.interfaces.IMolecule;
import org.openscience.cdk.libio.jena.BODO;
import org.openscience.cdk.libio.jena.CDK;
import org.openscience.cdk.qsar.DescriptorSpecification;
import org.openscience.cdk.qsar.DescriptorValue;
import org.openscience.cdk.qsar.result.DoubleArrayResult;
import org.openscience.cdk.qsar.result.IDescriptorResult;
import org.openscience.cdk.qsar.result.IntegerArrayResult;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.vocabulary.DC;
import com.hp.hpl.jena.vocabulary.OWL;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;

public class Convertor {

    public static Model molecule2Model(IMolecule molecule) {
        Model model = createCDKModel();
        Resource subject = model.createResource(
            createIdentifier(model, molecule)
        );
        model.add(subject, RDF.type, CDK.Molecule);
        serializeQSARDescriptors(model, subject, molecule);
        return model;
    }

    private static String createIdentifier(Model model, IChemObject object) {
        StringBuilder result = new StringBuilder();
        result.append("http://example.com/");
        result.append(model.hashCode()).append('/');
        result.append(object.getClass().getSimpleName()).append('/');
        result.append(object.hashCode());
        return result.toString();
    }

    private static Model createCDKModel() {
        Model model = ModelFactory.createOntologyModel();
        model.setNsPrefix("cdk", "http://cdk.sourceforge.net/model.owl#");
        return model;
    }

    private static void serializeQSARDescriptors(Model model,
            Resource rdfMolecule, IChemObject object) {
        model.setNsPrefix("bodo", BODO.URI);
        Map<Object,Object> props = object.getProperties();
        Iterator<Object> keys = props.keySet().iterator();
        while (keys.hasNext()) {
            Object key = keys.next();
            if (key instanceof DescriptorSpecification) {
                DescriptorSpecification specs = (DescriptorSpecification)key;
                DescriptorValue value = (DescriptorValue)props.get(key);
                IDescriptorResult result = value.getValue();
                Resource rdfValue = model.createResource();
                rdfValue.addProperty(RDF.type, CHEMINF.DescriptorValue);
                rdfMolecule.addProperty(BODO.hasDescriptorValue, rdfValue);
                // setup up the metadata list
                Resource rdfDescImpl = model.createResource();
                rdfDescImpl.addProperty(
                    RDF.type, BODO.DescriptorImplementation
                );
                rdfValue.addProperty(BODO.isCalculatedBy, rdfDescImpl);
                rdfDescImpl.addLiteral(
                    BODO.hasVendor, specs.getImplementationVendor()
                );
                rdfDescImpl.addLiteral(
                    DC.identifier, specs.getImplementationIdentifier()
                );
                rdfDescImpl.addLiteral(
                    DC.title, specs.getImplementationTitle()
                );
                Resource rdfAlgorithm = model.createResource();
                rdfAlgorithm.addProperty(OWL.sameAs,
                    model.createResource(specs.getSpecificationReference())
                );
                rdfAlgorithm.addProperty(RDF.type, CHEMINF.Algorithm);
                rdfDescImpl.addProperty(BODO.instanceOf, rdfAlgorithm);

                // add parameter setting to the metadata list
                Object[] params = value.getParameters();
//                logger.debug("Value: " + value.getSpecification().getImplementationIdentifier());
                if (params != null && params.length > 0) {
                    String[] paramNames = value.getParameterNames();
                    for (int i=0; i<params.length; i++) {
                        String paramName = paramNames[i];
                        Object paramVal = params[i];
                        if (paramName != null && paramVal != null) {
                            Resource rdfParamValue = model.createResource();
                            rdfParamValue.addProperty(RDF.type, BODO.ParameterValue);
                            Resource rdfParam = model.createResource();
                            rdfParam.addProperty(RDF.type, BODO.Parameter);
                            rdfParamValue.addProperty(BODO.valueFor, rdfParam);
                            rdfParamValue.addLiteral(
                                BODO.hasValue, paramVal.toString()
                            );
                            rdfParam.addProperty(RDFS.label, paramNames[i]);
                            rdfValue.addProperty(
                                BODO.isCalculatedWithParameter, rdfParamValue
                            );
                        }
                    }
                }
                if (result.length() == 1) {
                    createDescriptorPoint(model, rdfValue,
                            value.getNames()[0], result.toString());
                } else {
                    String[] descriptorNames = value.getNames();
                    if (result instanceof DoubleArrayResult) {
                        DoubleArrayResult array = (DoubleArrayResult)result;
                        for (int i=0; i<array.length(); i++) {
                            createDescriptorPoint(model, rdfValue,
                                    descriptorNames[i], array.get(i));
                        }
                    } else if (result instanceof IntegerArrayResult) {
                        IntegerArrayResult array = (IntegerArrayResult)result;
                        for (int i=0; i<array.length(); i++) {
                            createDescriptorPoint(model, rdfValue,
                                    descriptorNames[i], array.get(i));
                        }
                    } // else: no clue
                }
            }
        }
    }

    private static void createDescriptorPoint(Model model, Resource rdfValue,
            String descriptorName, Object value) {
        Resource point = model.createResource();
        point.addProperty(RDF.type, BODO.DescriptorValuePoint);
        rdfValue.addProperty(BODO.hasPart, point);
        point.addLiteral(
            BODO.hasValue, value
        );
        Resource descriptor = model.createResource();
        descriptor.addProperty(RDF.type, CHEMINF.Descriptor);
        descriptor.addLiteral(
            RDFS.label, descriptorName
        );
        point.addProperty(BODO.valuePointFor, descriptor);
    }

}