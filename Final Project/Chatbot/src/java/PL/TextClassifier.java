package PL;

import java.util.*;
import weka.core.*;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Attribute;
import weka.classifiers.*;
import weka.classifiers.Classifier;
import weka.filters.unsupervised.attribute.StringToWordVector;


public class TextClassifier {

    private String[]   inputText       = null;
    private String[]   inputClasses    = null;
    private String     classString     = null;

    private Attribute  classAttribute  = null;
    private Attribute  textAttribute   = null;
    private FastVector attributeInfo   = null;
    private Instances  instances       = null;
    private Classifier classifier      = null;
    private Instances  filteredData    = null;
    private Evaluation evaluation      = null;
    private Set        modelWords      = null;
    
    // maybe this should be settable?
    private String     delimitersStringToWordVector = "\\s.,:'\\\"()?!";

    //
    // main, mainly for testing
    //
    public static void main(String args[]) {
        String thisClassString = "weka.classifiers.lazy.IBk";

        if (args.length > 0) {
            thisClassString = args[0];
        } 

        String[] inputText =  {"hey!", "What is the course fee for HND IT?", "What are the courses you have?", "What is the duration for HND?","How many semester in BSc.Softwrae Engineering?",
                               "Do you have any professesional courses?","What are the online programs do you have?","Do you have any English programs?","Do you have short term courses?", 
                               "What are the short term courses do you have?", "When is the BSc IT new bacth start?", "How many modules in HND IT?", "How many modules in BSc SE?",
                               "How many modules in HND BM?", "What is the duration for HND BM?", "How many semester in HND Civil Engineering?", "Do you have a industril training for HND Civil Engineering?",
                               "What is the educational partner for the BEng( Hons) Biomedical Engineering?", "What is the educational partner for the BSc(Hons)Nursing?", "What are the facilities do you have?",
                               "Goodbye", "Thank you"};

        
        String[] inputClasses = {"hi","356000.00","HND,BSc,MSc","2Years","3 semester","Yes","Level 7 Post Graduate Diploma in Management","Yes, we have","Yes","IT, English", 
                                 "May 7th 2017", "17 modules", "5 modules", "21 modules", "18 months", "4 semester", "Yes", "Birmingham City University",
                                 "University of Sunderland", "Lecture Halls, Study Area, Library, Recreation Rooms, Laboratory", "Goodbye,Have a nice day", "Welcome"};
        
        String[] testText = {"What is the course fee for HND IT?","What is the duration for HND?", "What are the short term courses do you have","How many modules in BSc SE?","What is the educational partner for the BSc(Hons)Nursing?","What are the courses you have?", "Thank you", "Goodbye"};
        
        if (inputText.length != inputClasses.length) {
            System.err.println("The length of text and classes must be the same!");
            System.exit(1);
        }

        // calculate the classValues
        HashSet classSet = new HashSet(Arrays.asList(inputClasses));
        classSet.add("?");
        String[] classValues = (String[])classSet.toArray(new String[0]);

        // create class attribute

        FastVector classAttributeVector = new FastVector();
        for (int i = 0; i < classValues.length; i++) {
            classAttributeVector.addElement(classValues[i]);
        }
        Attribute thisClassAttribute = new Attribute("class", classAttributeVector);

        // create text attribute

        FastVector inputTextVector = null;  // null -> String type
        Attribute thisTextAttribute = new Attribute("text", inputTextVector);
        for (int i = 0; i < inputText.length; i++) {
            thisTextAttribute.addStringValue(inputText[i]);
        }

        // add the text of test cases
        for (int i = 0; i < testText.length; i++) {
            thisTextAttribute.addStringValue(testText[i]);
        }

        // create the attribute information

        FastVector thisAttributeInfo = new FastVector(2);
        thisAttributeInfo.addElement(thisTextAttribute);
        thisAttributeInfo.addElement(thisClassAttribute);

        TextClassifier classifier = new TextClassifier(inputText, inputClasses, thisAttributeInfo, thisTextAttribute, thisClassAttribute, thisClassString);

        System.out.println("DATA SET:\n");
        System.out.println(classifier.classify(thisClassString));

        System.out.println("NEW CASES:\n");
        System.out.println(classifier.classifyNewCases(testText));


    } // end main

    // constructor

    TextClassifier(String[] inputText, String[] inputClasses, FastVector attributeInfo, Attribute textAttribute, Attribute classAttribute, String classString) {
        this.inputText      = inputText;
        this.inputClasses   = inputClasses;
        this.classString    = classString;
        this.attributeInfo  = attributeInfo;
        this.textAttribute  = textAttribute;
        this.classAttribute = classAttribute;
    }

 
    // make classification and everything
 
    public StringBuffer classify() {

        if (classString == null || "".equals(classString)) {
            return(new StringBuffer());
        }

        return classify(classString);

    } // end classify()

    // the real classify method

    public StringBuffer classify(String classString) {
        
        this.classString = classString;

        StringBuffer result = new StringBuffer();

        // creates an empty instances set
        instances = new Instances("data set", attributeInfo, 100);

        // set which attribute is the class attribute
        instances.setClass(classAttribute);

        try {

            instances = populateInstances(inputText, inputClasses, instances, classAttribute, textAttribute);
            result.append("DATA SET:\n" + instances + "\n");

            // make filtered SparseData
            filteredData = filterText(instances);

            // create Set of modelWords
            modelWords = new HashSet();
            Enumeration enumx = filteredData.enumerateAttributes();
            while (enumx.hasMoreElements()) {
                Attribute att = (Attribute)enumx.nextElement();
                String attName = att.name().toLowerCase();
                modelWords.add(attName);
                
            }

            // Classify and evaluate data

            classifier = Classifier.forName(classString,null);

            classifier.buildClassifier(filteredData);
            evaluation = new Evaluation(filteredData);
            evaluation.evaluateModel(classifier, filteredData);

            result.append(printClassifierAndEvaluation(classifier, evaluation) + "\n");

            // check instances
            int startIx = 0;
            result.append(checkCases(filteredData, classifier, classAttribute, inputText, "not test", startIx)  + "\n");


        } catch (Exception e) {
            e.printStackTrace();
            result.append("\nException (sorry!):\n" + e.toString());
        }

        return result;

    } // end classify


    // test new unclassified examples

    public StringBuffer classifyNewCases(String[] tests) {

        StringBuffer result = new StringBuffer();

        // first copy the old instances, 
        // then add the test words

        Instances testCases = new Instances(instances);
        testCases.setClass(classAttribute);

        String[] testsWithModelWords = new String[tests.length];
        int gotModelWords = 0; // how many words will we use?

        for (int i = 0; i < tests.length; i++) {
            // the test string to use
            StringBuffer acceptedWordsThisLine = new StringBuffer();

            // split each line in the test array
            String[] splittedText = tests[i].split("["+delimitersStringToWordVector+"]");
            // check if word is a model word
            for (int wordIx = 0; wordIx < splittedText.length; wordIx++) {
                String sWord = splittedText[wordIx];
                if (modelWords.contains((String)sWord)) {
                    gotModelWords++;
                    acceptedWordsThisLine.append(sWord + " ");
                }
            }
            testsWithModelWords[i] = acceptedWordsThisLine.toString();
        }

        // should we do do something if there is no modelWords?
        if (gotModelWords == 0) {
            result.append("\nWarning!\nThe text to classify didn't contain a single\nword from the modelled words. This makes it hard for the classifier to\ndo something usefull.\nThe result may be weird.\n\n");
        }

        try {

            // add the ? class for all test cases
            String[] tmpClassValues = new String[tests.length];
            for (int i = 0; i < tmpClassValues.length; i++) {
                tmpClassValues[i] = "?";
            }

            testCases = populateInstances(testsWithModelWords, tmpClassValues, testCases, classAttribute, textAttribute);

            Instances filteredTests = filterText(testCases);

            // check
            int startIx = instances.numInstances();
            result.append(checkCases(filteredTests, classifier, classAttribute, tests, "newcase", startIx) + "\n");

        } catch (Exception e) {
            e.printStackTrace();
            result.append("\nException (sorry!):\n" + e.toString());
        }

        return result;

    } //  end classifyNewCases


    //  from empty instances populate with text and class arrays

    public static Instances populateInstances(String[] theseInputTexts, String[] theseInputClasses, Instances theseInstances, Attribute classAttribute, Attribute textAttribute) {

        for (int i = 0; i < theseInputTexts.length; i++) {
            Instance inst = new Instance(2);
            inst.setValue(textAttribute,theseInputTexts[i]);
            if (theseInputClasses != null && theseInputClasses.length > 0) {
                inst.setValue(classAttribute, theseInputClasses[i]);
            }
            theseInstances.add(inst);
        }

        return theseInstances;


    } // populateInstances


    // check instances (full set or just test cases)

    public static StringBuffer checkCases(Instances theseInstances, Classifier thisClassifier, Attribute thisClassAttribute, String[] texts, String testType, int startIx) {
        
        StringBuffer result = new StringBuffer();

        try {

            result.append("\nCHECKING ALL THE INSTANCES:\n");

            Enumeration enumClasses = thisClassAttribute.enumerateValues();
            result.append("Class values (in order): ");
            while (enumClasses.hasMoreElements()) {
                String classStr = (String)enumClasses.nextElement();
                result.append("'" + classStr + "'  ");
            }
            result.append("\n");

            // startIx is a fix for handling text cases
            for (int i = startIx; i < theseInstances.numInstances(); i++) {

                SparseInstance sparseInst = new SparseInstance(theseInstances.instance(i));
                sparseInst.setDataset(theseInstances);

                result.append("\nTesting: '" + texts[i-startIx] + "'\n");

                double correctValue = (double)sparseInst.classValue();
                double predictedValue = thisClassifier.classifyInstance(sparseInst);

                String predictString = thisClassAttribute.value((int)predictedValue) + " (" + predictedValue + ")";
                result.append("predicted: '" + predictString);
                // print comparison if not new case
                if (!"newcase".equals(testType)) {
                    String correctString = thisClassAttribute.value((int)correctValue) + " (" + correctValue + ")";
                    String testString = ((predictedValue == correctValue) ? "OK!" : "NOT OK!") + "!";
                    result.append("' real class: '" + correctString +  "' ==> " +  testString);
                }
                result.append("\n");
                result.append("\n");
            }

        } catch (Exception e) {
            e.printStackTrace();
            result.append("\nException (sorry!):\n" + e.toString());
        }
        
        return result;

    } // end checkCases

    // take instances in normal format (strings) and convert to Sparse format

    public static Instances filterText(Instances theseInstances) {

        StringToWordVector filter = null;
        // default values according to Java Doc:
        int wordsToKeep = 1000;

        Instances filtered = null;
        try {

            filter = new StringToWordVector(wordsToKeep);
            filter.setOutputWordCounts(true);
            filter.setSelectedRange("1");
            
            filter.setInputFormat(theseInstances);
            
            filtered = weka.filters.Filter.useFilter(theseInstances,filter);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return filtered;
        
    } // end filterText

    // information about classifier and evaluation
    public static StringBuffer printClassifierAndEvaluation(Classifier thisClassifier, Evaluation thisEvaluation) {

        StringBuffer result = new StringBuffer();

        try {
            result.append("\n\nINFORMATION ABOUT THE CLASSIFIER AND EVALUATION:\n");
            result.append("\nclassifier.toString():\n" + thisClassifier.toString() + "\n");
            result.append("\nevaluation.toSummaryString(title, false):\n" + thisEvaluation.toSummaryString("Summary",false)  + "\n");
            result.append("\nevaluation.toMatrixString():\n" + thisEvaluation.toMatrixString()  + "\n");
            result.append("\nevaluation.toClassDetailsString():\n" + thisEvaluation.toClassDetailsString("Details")  + "\n");
            result.append("\nevaluation.toCumulativeMarginDistribution:\n" + thisEvaluation.toCumulativeMarginDistributionString()  + "\n");
        } catch (Exception e) {
            e.printStackTrace();
            result.append("\nException (sorry!):\n" + e.toString());
        }

        return result;

    } // end printClassifierAndEvaluation

    // setter for the classifier _string_

    public void setClassifierString(String classString) {
        this.classString = classString;
    }
   

} // end class TextClassifier

