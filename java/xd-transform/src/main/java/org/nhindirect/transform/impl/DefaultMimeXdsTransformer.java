/* 
 * Copyright (c) 2010, NHIN Direct Project
 * All rights reserved.
 *  
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright 
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright 
 *    notice, this list of conditions and the following disclaimer in the 
 *    documentation and/or other materials provided with the distribution.  
 * 3. Neither the name of the the NHIN Direct Project (nhindirect.org)
 *    nor the names of its contributors may be used to endorse or promote products 
 *    derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY 
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY 
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND 
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT 
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS 
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.nhindirect.transform.impl;

import static org.nhindirect.transform.util.XdConstants.ASSOCIATION_TYPE_1;
import static org.nhindirect.transform.util.XdConstants.AUTHOR_INSTITUTION;
import static org.nhindirect.transform.util.XdConstants.AUTHOR_PERSON;
import static org.nhindirect.transform.util.XdConstants.AUTHOR_ROLE;
import static org.nhindirect.transform.util.XdConstants.CCD_EXTENSION;
import static org.nhindirect.transform.util.XdConstants.CCD_XMLNS;
import static org.nhindirect.transform.util.XdConstants.CLASSIFICATION_TYPE;
import static org.nhindirect.transform.util.XdConstants.CODING_SCHEME;
import static org.nhindirect.transform.util.XdConstants.CREATION_TIME;
import static org.nhindirect.transform.util.XdConstants.DEFAULT_CLASS_CODE;
import static org.nhindirect.transform.util.XdConstants.DEFAULT_FACILITY_CODE;
import static org.nhindirect.transform.util.XdConstants.DEFAULT_LOINC_CODE;
import static org.nhindirect.transform.util.XdConstants.DEFAULT_PRACTICE_SETTING_CODE;
import static org.nhindirect.transform.util.XdConstants.EXTRINSIC_OBJECT_TYPE;
import static org.nhindirect.transform.util.XdConstants.IDENTIFIABLE_TYPE_NS;
import static org.nhindirect.transform.util.XdConstants.LOINC;
import static org.nhindirect.transform.util.XdConstants.REGISTRY_PACKAGE_TYPE;
import static org.nhindirect.transform.util.XdConstants.SOURCE_PATIENT_ID;
import static org.nhindirect.transform.util.XdConstants.SOURCE_PATIENT_INFO;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType.Document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import oasis.names.tc.ebxml_regrep.xsd.lcm._3.SubmitObjectsRequest;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.AssociationType1;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ClassificationType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExternalIdentifierType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExtrinsicObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.IdentifiableType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.InternationalStringType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.LocalizedStringType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectListType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryPackageType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.SlotType1;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ValueListType;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nhindirect.transform.MimeXdsTransformer;
import org.nhindirect.transform.XdmXdsTransformer;
import org.nhindirect.transform.exception.TransformationException;
import org.nhindirect.transform.parse.ccd.CcdParser;
import org.nhindirect.transform.pojo.SimplePerson;
import org.nhindirect.transform.util.type.Association;
import org.nhindirect.transform.util.type.ClassificationNode;
import org.nhindirect.transform.util.type.ExternalClassificationScheme;
import org.nhindirect.transform.util.type.ExternalIdentifier;
import org.nhindirect.transform.util.type.MimeType;

/*
 * FIXME
 * 
 * The system currently handles multiple documents and recipients. 
 * 
 * Each document is placed into its own ProvideAndRegisterDocumentSetRequestType 
 * object, and correspondingly its own SOAP message. 
 * 
 * ProvideAndRegisterDocumentSetRequestType allows for multiple documents in a 
 * single request, and this class should eventually be updated to support this.
 */

/**
 * Transform a MimeMessage into a XDS request.
 * 
 * @author vlewis
 */
public class DefaultMimeXdsTransformer implements MimeXdsTransformer
{
    private static final String CODE_FORMAT_TEXT = "TEXT";
    private static final String CODE_FORMAT_CDAR2 = "CDAR2/IHE 1.0";

    private byte[] xdsDocument = null;
    private String xdsMimeType = null;
    private String xdsFormatCode = null;

    private XdmXdsTransformer xdmXdsTransformer = new DefaultXdmXdsTransformer();

    private static final Log LOGGER = LogFactory.getFactory().getInstance(DefaultMimeXdsTransformer.class);

    /**
     * Construct a new DefaultMimeXdsTransformer object.
     */
    public DefaultMimeXdsTransformer()
    {
        super();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.nhindirect.transform.MimeXdsTransformer#transform(javax.mail.internet
     * .MimeMessage)
     */
    @Override
    public List<ProvideAndRegisterDocumentSetRequestType> transform(MimeMessage mimeMessage)
            throws TransformationException
    {
        List<ProvideAndRegisterDocumentSetRequestType> requests = new ArrayList<ProvideAndRegisterDocumentSetRequestType>();

        try
        {
            Date sentDate = mimeMessage.getSentDate();
            String subject = mimeMessage.getSubject();

            String from = mimeMessage.getFrom()[0].toString();
            Address[] recipients = mimeMessage.getAllRecipients();

            // Plain mail (no attachments)
            if (MimeType.TEXT_PLAIN.matches(mimeMessage.getContentType()))
            {
                LOGGER.info("Handling plain mail (no attachments)");

                xdsFormatCode = CODE_FORMAT_TEXT;
                xdsMimeType = MimeType.TEXT_PLAIN.getType();
                xdsDocument = ((String) mimeMessage.getContent()).getBytes();

                List<ProvideAndRegisterDocumentSetRequestType> items = getRequests(subject, sentDate, from, recipients);
                requests.addAll(items);
            }
            // Multipart/mixed (attachments)
            else if (MimeType.MULTIPART_MIXED.matches(mimeMessage.getContentType()))
            {
                LOGGER.info("Handling multipart/mixed");

                MimeMultipart mimeMultipart = (MimeMultipart) mimeMessage.getContent();

                // For each BodyPart
                for (int i = 0; i < mimeMultipart.getCount(); i++)
                {
                    BodyPart bodyPart = mimeMultipart.getBodyPart(i);

                    // Skip empty BodyParts
                    if (bodyPart.getSize() <= 0)
                    {
                        LOGGER.warn("Empty body, skipping");
                        continue;
                    }

                    // Skip empty file names
                    if (StringUtils.isBlank(bodyPart.getFileName())
                            || StringUtils.equalsIgnoreCase(bodyPart.getFileName(), "null"))
                    {
                        LOGGER.warn("Filename is blank, skipping");
                        continue;
                    }

                    if (LOGGER.isInfoEnabled())
                        LOGGER.info("File name: " + bodyPart.getFileName());

                    if (StringUtils.contains(bodyPart.getFileName(), ".zip"))
                    {
                        try
                        {
                            LOGGER.info("Bodypart is an XDM request");

                            ProvideAndRegisterDocumentSetRequestType request = getXDMRequest(bodyPart);
                            requests.add(request);
                        }
                        catch (Exception x)
                        {
                            LOGGER.warn("Handling of assumed XDM request failed, skipping");
                        }

                        continue;
                    }

                    InputStream inputStream = bodyPart.getInputStream();
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                    int data = 0;
                    byte[] buffer = new byte[1024];
                    while ((data = inputStream.read(buffer)) != -1)
                    {
                        outputStream.write(buffer, 0, data);
                    }

                    String contentString = new String(outputStream.toByteArray());

                    LOGGER.info("Content type is " + bodyPart.getContentType());

                    // special handling for recognized content types
                    if (MimeType.TEXT_PLAIN.matches(bodyPart.getContentType()))
                    {
                        LOGGER.info("Matched type TEXT_PLAIN");

                        if (StringUtils.isBlank(contentString))
                        {
                            continue; // skip 'empty' parts
                        }

                        xdsFormatCode = CODE_FORMAT_TEXT;
                        xdsMimeType = MimeType.TEXT_PLAIN.getType();
                        xdsDocument = outputStream.toByteArray();

                        List<ProvideAndRegisterDocumentSetRequestType> items = getRequests(subject, mimeMessage
                                .getSentDate(), from, recipients);
                        requests.addAll(items);
                    }
                    else if (MimeType.TEXT_XML.matches(bodyPart.getContentType()))
                    {
                        LOGGER.info("Matched type TEXT_XML");

                        if (StringUtils.contains(contentString, CCD_XMLNS)
                                && StringUtils.contains(contentString, CCD_EXTENSION))
                        {
                            LOGGER.info("Matched format CODE_FORMAT_CDAR2");

                            xdsFormatCode = CODE_FORMAT_CDAR2;
                            xdsMimeType = MimeType.TEXT_XML.getType();
                            xdsDocument = outputStream.toByteArray();
                        }
                        else
                        {
                            // Other XML (possible CCR or HL7)
                            LOGGER.info("Defaulted to format CODE_FORMAT_TEXT");

                            xdsFormatCode = CODE_FORMAT_TEXT;
                            xdsMimeType = MimeType.TEXT_XML.getType();
                            xdsDocument = outputStream.toByteArray();
                        }

                        // TODO: support more XML types

                        List<ProvideAndRegisterDocumentSetRequestType> items = getRequests(subject, mimeMessage
                                .getSentDate(), from, recipients);
                        requests.addAll(items);
                    }
                    else
                    {
                        LOGGER.info("Did not match a type");

                        // Otherwise make best effort passing MIME content type

                        xdsFormatCode = CODE_FORMAT_TEXT;
                        xdsMimeType = bodyPart.getContentType();
                        xdsDocument = outputStream.toByteArray();

                        List<ProvideAndRegisterDocumentSetRequestType> items = getRequests(subject, mimeMessage
                                .getSentDate(), from, recipients);
                        requests.addAll(items);
                    }
                }
            }
            else
            {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Message content type (" + mimeMessage.getContentType()
                            + ") is not supported, skipping");
            }
        }
        catch (MessagingException e)
        {
            if (LOGGER.isErrorEnabled())
                LOGGER.error("Unexpected MessagingException occured while handling MimeMessage", e);
            throw new TransformationException("Unable to complete transformation.", e);
        }
        catch (IOException e)
        {
            if (LOGGER.isErrorEnabled())
                LOGGER.error("Unexpected IOException occured while handling MimeMessage", e);
            throw new TransformationException("Unable to complete transformation.", e);
        }

        return requests;
    }

    /**
     * Get an XDM Request from a BodyPart object.
     * 
     * @param bodyPart
     *            The BodyPart object containing the XDM request.
     * @return a ProvideAndRegisterDocumentSetRequestType object.
     * @throws Exception
     */
    protected ProvideAndRegisterDocumentSetRequestType getXDMRequest(BodyPart bodyPart) throws Exception
    {
        LOGGER.trace("Inside getMDMRequest");

        DataHandler dh = bodyPart.getDataHandler();

        return xdmXdsTransformer.transform(dh);
    }

    /**
     * Create a list of ProvideAndRegisterDocumentSetRequestType objects from
     * the provided data for each of the provided recipients.
     * 
     * The proof-of-concept code will look for recipients that match
     * (.*)@xd\.(.*) and transform them to $1@$2 before creating the request.
     * 
     * @param subject
     *            The message subject.
     * @param sentDate
     *            The message sent date.
     * @param auth
     *            The author of the document.
     * @param recipients
     *            The list of recipients to receive the XDS request.
     * @return a list of ProvideAndRegisterDocumentSetRequestType objects.
     */
    protected List<ProvideAndRegisterDocumentSetRequestType> getRequests(String subject, Date sentDate, String auth,
            Address[] recipients)
    {
        List<ProvideAndRegisterDocumentSetRequestType> requests = new ArrayList<ProvideAndRegisterDocumentSetRequestType>();

        for (Address recipient : recipients)
        {
            String realRecipient = StringUtils.replace(recipient.toString(), "@xd.", "@");

            try
            {
                ProvideAndRegisterDocumentSetRequestType request = getRequest(subject, sentDate, auth, realRecipient);
                requests.add(request);
            }
            catch (Exception e)
            {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Error creating ProvideAndRegisterDocumentSetRequestType object, skipping", e);
            }
        }

        return requests;
    }

    /**
     * Create a single ProvideAndRegisterDocumentSetRequestType objects from the
     * provided data.
     * 
     * @param subject
     *            The message subject.
     * @param sentDate
     *            The message sent date.
     * @param auth
     *            The author of the document.
     * @param recip
     *            The recipient of the document.
     * @return a single ProvideAndRegisterDocumentSetRequestType object.
     * @throws Exception
     */
    protected ProvideAndRegisterDocumentSetRequestType getRequest(String subject, Date sentDate, String auth,
            String recip) throws Exception
    {
        ProvideAndRegisterDocumentSetRequestType prsr = new ProvideAndRegisterDocumentSetRequestType();

        String sdoc = new String(xdsDocument);
        CcdParser cp = new CcdParser();
        cp.parse(sdoc);

        String patientId = cp.getPatientId();
        String orgId = cp.getOrgId();
        String date = formatDate(sentDate);
        String subId = UUID.randomUUID().toString();
        String docId = UUID.randomUUID().toString();
        SimplePerson sp = null;

        SubmitObjectsRequest sor = getSubmitObjectsRequest(patientId, orgId, sp, subject, date, docId, subId,
                xdsFormatCode, xdsMimeType, auth, recip);
        prsr.setSubmitObjectsRequest(sor);

        DataSource source = new ByteArrayDataSource(xdsDocument, MimeType.APPLICATION_XML + "; charset=UTF-8");
        DataHandler dhnew = new DataHandler(source);

        List<Document> docs = prsr.getDocument();
        Document pdoc = new Document();

        pdoc.setValue(dhnew);
        pdoc.setId(docId);
        docs.add(pdoc);

        return prsr;
    }

    /**
     * Create a SubmitObjectsRequest object for the XDS request using the
     * provided data.
     * 
     * @param patientId
     *            The patient ID for the document.
     * @param orgId
     *            The organization ID for the document.
     * @param person
     *            The SimplePerson object for the document.
     * @param subject
     *            The message subject.
     * @param sentDate
     *            The message sent date.
     * @param docId
     *            The unique document ID.
     * @param subId
     *            The submission ID.
     * @param formatCode
     *            The document format code.
     * @param mimeType
     *            The document MIME type.
     * @param auth
     *            The document author.
     * @param recip
     *            The recipient of the document.
     * @return a SubmitObjectsRequest object.
     */
    protected SubmitObjectsRequest getSubmitObjectsRequest(String patientId, String orgId, SimplePerson person,
            String subject, String sentDate, String docId, String subId, String formatCode, String mimeType,
            String auth, String recip)
    {
        SubmitObjectsRequest req = new SubmitObjectsRequest();
        RegistryObjectListType rolt = new RegistryObjectListType();
        List<JAXBElement<? extends IdentifiableType>> elems = rolt.getIdentifiable();

        LOGGER.info("Creating ExtrinsicObjectType object inside getSubmitObjectsRequest");
        ExtrinsicObjectType eot = getExtrinsicObject(patientId, orgId, person, sentDate, docId, formatCode, mimeType,
                auth);
        QName qname = new QName(IDENTIFIABLE_TYPE_NS, EXTRINSIC_OBJECT_TYPE);
        JAXBElement<ExtrinsicObjectType> eotj = new JAXBElement<ExtrinsicObjectType>(qname, ExtrinsicObjectType.class,
                eot);

        LOGGER.info("Creating RegistryPackageType object inside getSubmitObjectsRequest");
        RegistryPackageType rpt = getSubmissionSet(patientId, orgId, subject, sentDate, subId, auth, recip);
        qname = new QName(IDENTIFIABLE_TYPE_NS, REGISTRY_PACKAGE_TYPE);
        JAXBElement<RegistryPackageType> rptj = new JAXBElement<RegistryPackageType>(qname, RegistryPackageType.class,
                rpt);

        LOGGER.info("Creating ClassificationType object inside getSubmitObjectsRequest");
        ClassificationType clas = getClassification(ClassificationNode.SUBMISSION_SET, rpt.getId());
        qname = new QName(IDENTIFIABLE_TYPE_NS, CLASSIFICATION_TYPE);
        JAXBElement<ClassificationType> clasj = new JAXBElement<ClassificationType>(qname, ClassificationType.class,
                clas);

        LOGGER.info("Creating AssociationType1 object inside getSubmitObjectsRequest");
        AssociationType1 ass = getAssociation(Association.HAS_MEMBER, rpt.getId(), eot.getId());
        qname = new QName(IDENTIFIABLE_TYPE_NS, ASSOCIATION_TYPE_1);
        JAXBElement<AssociationType1> assj = new JAXBElement<AssociationType1>(qname, AssociationType1.class, ass);

        LOGGER.info("Building JAXBElements list");
        elems.add(eotj);
        elems.add(rptj);
        elems.add(clasj);
        elems.add(assj);

        LOGGER.info("Building SubmitObjectsRequest object");
        req.setRegistryObjectList(rolt);

        return req;
    }

    /**
     * Create an EntrinsicObjectType object for the XDS request using the
     * provided data.
     * 
     * @param patientId
     *            The patient ID for the document.
     * @param orgId
     *            The organization ID for the document.
     * @param person
     *            The SimplePerson object for the document.
     * @param sentDate
     *            The message sent date.
     * @param docId
     *            The unique document ID.
     * @param formatCode
     *            The document format code.
     * @param mimeType
     *            The document MIME type.
     * @param auth
     *            The document author.
     * @return an EntrinsicObjectType object.
     */
    protected ExtrinsicObjectType getExtrinsicObject(String patientId, String orgId, SimplePerson person,
            String sentDate, String docId, String formatCode, String mimeType, String auth)
    {
        List<String> snames = null;
        List<String> slotNames = null;
        List<String> slotValues = null;
        List<SlotType1> slots = null;
        List<ClassificationType> classifs = null;
        List<ExternalIdentifierType> extIds = null;

        ExtrinsicObjectType document = null;

        document = new ExtrinsicObjectType();
        document.setId(docId);
        document.setObjectType(ClassificationNode.DOCUMENT_ENTRY.getScheme());
        document.setMimeType(mimeType);

        slots = document.getSlot();
        extIds = document.getExternalIdentifier();
        classifs = document.getClassification();

        slots.add(makeSlot(CREATION_TIME, sentDate));

        slots.add(makeSlot(SOURCE_PATIENT_ID, patientId + "^^^&" + orgId));

        if (person != null)
        {
            slots.add(makePatientSlot(SOURCE_PATIENT_INFO, person, patientId, orgId));
        }

        snames = new ArrayList<String>();
        slotNames = new ArrayList<String>();
        slotValues = new ArrayList<String>();

        if (auth != null)
        {
            snames.add(null);
            slotNames.add(AUTHOR_PERSON);
            slotValues.add(auth);
        }

        snames.add(null);
        slotNames.add(AUTHOR_INSTITUTION);
        slotValues.add(orgId);

        snames.add(null);
        slotNames.add(AUTHOR_ROLE);
        if (auth != null)
            slotValues.add(auth + "'s Role");// see if we need this
        else
            slotValues.add("System");

        addClassifications(classifs, docId, ExternalClassificationScheme.DOCUMENT_ENTRY_AUTHOR, null, slotNames,
                slotValues, snames);

        addClassifications(classifs, docId, ExternalClassificationScheme.DOCUMENT_ENTRY_CLASS_CODE, DEFAULT_CLASS_CODE,
                CODING_SCHEME, "Connect-a-thon classCodes", DEFAULT_CLASS_CODE);

        addClassifications(classifs, docId, ExternalClassificationScheme.DOCUMENT_ENTRY_FORMAT_CODE, formatCode,
                CODING_SCHEME, "Connect-a-thon formatCodes", formatCode);

        addClassifications(classifs, docId, ExternalClassificationScheme.DOCUMENT_ENTRY_FACILITY_CODE,
                DEFAULT_FACILITY_CODE, CODING_SCHEME, "Connect-a-thon healthcareFacilityTypeCodes",
                DEFAULT_FACILITY_CODE);

        addClassifications(classifs, docId, ExternalClassificationScheme.DOCUMENT_ENTRY_PRACTICE_SETTING_CODE,
                DEFAULT_PRACTICE_SETTING_CODE, CODING_SCHEME, "Connect-a-thon practiceSettingCodes",
                DEFAULT_PRACTICE_SETTING_CODE);

        addClassifications(classifs, docId, ExternalClassificationScheme.DOCUMENT_ENTRY_TYPE_CODE, DEFAULT_LOINC_CODE,
                CODING_SCHEME, LOINC, DEFAULT_LOINC_CODE);

        addExternalIds(extIds, docId, ExternalIdentifier.DOCUMENT_ENTRY_PATIENT_ID, patientId + "^^^&" + orgId);
        addExternalIds(extIds, docId, ExternalIdentifier.DOCUMENT_ENTRY_UNIQUE_ID, docId);

        return document;
    }

    /**
     * Create a RegistryPackageType object for the XDS request from the provided
     * data.
     * 
     * @param patientId
     *            The patient ID for the document.
     * @param orgId
     *            The organization ID for the document.
     * @param subject
     *            The message subject.
     * @param sentDate
     *            The message sent date.
     * @param subId
     *            The submission ID.
     * @param auth
     *            The document author.
     * @param recip
     *            The recipient of the document.
     * @return a RegistryPackageType object.
     */
    protected RegistryPackageType getSubmissionSet(String patientId, String orgId, String subject, String sentDate,
            String subId, String auth, String recip)
    {
        List<String> snames = null;
        List<String> slotNames = null;
        List<String> slotValues = null;
        List<SlotType1> slots = null;
        List<ClassificationType> classifs = null;
        List<ExternalIdentifierType> extIds = null;

        RegistryPackageType subset = new RegistryPackageType();

        subset.setId(subId);
        subset.setObjectType(ClassificationNode.DOCUMENT_ENTRY.getScheme());

        slots = subset.getSlot();
        extIds = subset.getExternalIdentifier();
        classifs = subset.getClassification();

        slots.add(makeSlot("submissionTime", sentDate));
        String intendedRecipient = "|" + recip + "^last^first^^^prefix^^^&amp;1.3.6.1.4.1.21367.3100.1&amp;ISO";
        slots.add(makeSlot("intendedRecipient", intendedRecipient));

        snames = new ArrayList<String>();
        slotNames = new ArrayList<String>();
        slotValues = new ArrayList<String>();

        if (auth != null)
        {
            snames.add(null);
            slotNames.add(AUTHOR_PERSON);
            slotValues.add(auth);
        }

        snames.add(null);
        slotNames.add(AUTHOR_INSTITUTION);
        slotValues.add(orgId);

        snames.add(null);
        slotNames.add(AUTHOR_ROLE);
        if (auth != null)
            slotValues.add(auth + "'s Role");// see if we need this
        else
            slotValues.add("System");

        addClassifications(classifs, subId, ExternalClassificationScheme.SUBMISSION_SET_AUTHOR, null, slotNames,
                slotValues, snames);

        addClassifications(classifs, subId, ExternalClassificationScheme.SUBMISSION_SET_CONTENT_TYPE_CODE, subject,
                CODING_SCHEME, "Connect-a-thon contentTypeCodes", subject);

        addExternalIds(extIds, subId, ExternalIdentifier.SUBMISSION_SET_UNIQUE_ID, subId);
        addExternalIds(extIds, subId, ExternalIdentifier.SUBMISSION_SET_SOURCE_ID, orgId);
        addExternalIds(extIds, subId, ExternalIdentifier.SUBMISSION_SET_PATIENT_ID, patientId + "^^^&" + orgId);

        return subset;
    }

    /**
     * Create a ClassificationType object from the given classificationNode and
     * classifiedObject values.
     * 
     * @param classificationNode
     *            The ClassificationNodeEnum from which to extract the scheme..
     * @param classifiedObject
     *            The value to set for classifiedObject.
     * @return
     */
    protected ClassificationType getClassification(ClassificationNode classificationNode, String classifiedObject)
    {
        ClassificationType ct = new ClassificationType();
        ct.setClassificationNode(classificationNode.getScheme());
        ct.setClassifiedObject(classifiedObject);

        return ct;
    }

    /**
     * Create an AssociationType1 object using the given source object ID and
     * document ID.
     * 
     * @param setId
     *            The source object ID.
     * @param docId
     *            The target object ID.
     * @return an AssociationType1 object with the given source object ID and
     *         document ID.
     */
    protected AssociationType1 getAssociation(Association association, String setId, String docId)
    {
        AssociationType1 at = new AssociationType1();
        at.setAssociationType(association.getAssociationType());
        at.setSourceObject(setId);
        at.setTargetObject(docId);

        List<SlotType1> slots = at.getSlot();
        slots.add(makeSlot("SubmissionSetStatus", "Original"));

        return at;
    }

    /**
     * Create a SlotType1 object using the provided patient information.
     * 
     * @param name
     *            The slot name.
     * @param patient
     *            The SimplePerson object representing a patient.
     * @param patientId
     *            The patient ID.
     * @param orgId
     *            The organization ID.
     * @return a SlotType1 object containing the provided patient data.
     */
    protected SlotType1 makePatientSlot(String name, SimplePerson patient, String patientId, String orgId)
    {
        List<String> vals = null;
        SlotType1 slot = new SlotType1();
        ValueListType values = new ValueListType();

        slot.setName(name);
        slot.setValueList(values);
        vals = values.getValue();

        /*
         * TODO: What should happen if patient is null?
         */
        if (patient != null)
        {
            StringBuffer sb = null;

            // <rim:Value>PID-3|pid1^^^domain</rim:Value>
            sb = new StringBuffer("PID-3|");
            sb.append(patientId);
            sb.append("^^^&amp;");
            sb.append(orgId);
            sb.append("&amp;ISO");
            vals.add(sb.toString());

            // <rim:Value>PID-5|Doe^John^^^</rim:Value>
            sb = new StringBuffer("PID-5|");
            sb.append(patient.getLastName());
            sb.append("^");
            sb.append(patient.getFirstName());
            sb.append("^");
            sb.append(patient.getMiddleName());
            vals.add(sb.toString());

            // <rim:Value>PID-7|19560527</rim:Value>
            sb = new StringBuffer("PID-7|");
            sb.append(formatDateFromMDM(patient.getBirthDateTime()));
            vals.add(sb.toString());

            // <rim:Value>PID-8|M</rim:Value>
            sb = new StringBuffer("PID-8|");
            sb.append(patient.getGenderCode());
            vals.add(sb.toString());

            // <rim:Value>PID-11|100 Main
            // St^^Metropolis^Il^44130^USA</rim:Value>
            sb = new StringBuffer("PID-11|");
            sb.append(patient.getStreetAddress1());
            sb.append("^");
            sb.append(patient.getCity());
            sb.append("^^");
            sb.append(patient.getState());
            sb.append("^");
            sb.append(patient.getZipCode());
            sb.append("^");
            sb.append(patient.getCountry());
            vals.add(sb.toString());
        }

        return slot;
    }

    /**
     * Add a ClassificationType object to the provided list of
     * ClassificationType objects, created from the provided data.
     * 
     * @param classifs
     *            The list of ClassificationType objects to append to.
     * @param docId
     *            The document ID.
     * @param externalClassificationScheme
     *            The external classification scheme object to use for this
     *            ClassificationType.
     * @param rep
     *            The node representation.
     * @param slotName
     *            The slot name.
     * @param slotValue
     *            The slot value.
     * @param sname
     *            The localized string.
     */
    protected void addClassifications(List<ClassificationType> classifs, String docId,
            ExternalClassificationScheme externalClassificationScheme, String rep, String slotName, String slotValue,
            String sname)
    {
        List<String> slotNames = Arrays.asList(slotName);
        List<String> slotValues = Arrays.asList(slotValue);
        List<String> snames = Arrays.asList(sname);
        
        addClassifications(classifs, docId, ExternalClassificationScheme.DOCUMENT_ENTRY_CLASS_CODE, rep, slotNames,
                slotValues, snames);
    }

    /**
     * Add a ClassificationType object to the provided list of
     * ClassificationType objects, created from the provided data.
     * 
     * @param classifs
     *            The list of ClassificationType objects to append to.
     * @param docId
     *            The document ID.
     * @param externalClassificationScheme
     *            The external classification scheme object to use for this
     *            ClassificationType.
     * @param rep
     *            The node representation.
     * @param slotNames
     *            The list of slot names.
     * @param slotValues
     *            The list of slot values.
     * @param snames
     *            The localized strings.
     */
    protected void addClassifications(List<ClassificationType> classifs, String docId,
            ExternalClassificationScheme externalClassificationScheme, String rep, List<String> slotNames,
            List<String> slotValues, List<String> snames)
    {
        if (classifs == null)
        {
            throw new IllegalArgumentException("Must include a live reference to a ClassificationType list");
        }

        ClassificationType ct = new ClassificationType();

        classifs.add(ct);
        ct.setClassifiedObject(docId);
        ct.setClassificationScheme(externalClassificationScheme.getClassificationScheme());
        ct.setId(externalClassificationScheme.getClassificationId());
        ct.setNodeRepresentation(rep);

        List<SlotType1> slots = ct.getSlot();
        Iterator<String> is = slotNames.iterator();

        int i = 0;
        while (is.hasNext())
        {
            String slotName = is.next();
            SlotType1 slot = makeSlot(slotName, (String) slotValues.get(i));
            slots.add(slot);

            String sname = (String) snames.get(i);
            if (sname != null)
            {
                InternationalStringType name = new InternationalStringType();
                List<LocalizedStringType> names = name.getLocalizedString();
                LocalizedStringType lname = new LocalizedStringType();
                lname.setValue(sname);
                names.add(lname);
                ct.setName(name);
            }

            i++;
        }

    }

    /**
     * Add a ExternalIdentifierType object to the provided list of
     * ExternalIdentifierType objects, created from the provided data.
     * 
     * @param extIds
     *            The list of ExternalIdentifierType objects to append to.
     * @param docId
     *            The document ID.
     * @param scheme
     *            The identification scheme.
     * @param id
     *            The external identifier ID.
     * @param sname
     *            The localized string.
     * @param value
     *            The external identifier value.
     */
    protected void addExternalIds(List<ExternalIdentifierType> extIds, String docId,
            ExternalIdentifier externalIdentifier, String value)
    {
        if (extIds == null)
            throw new IllegalArgumentException("Must include a live reference to an ExternalIdentifierType list");

        ExternalIdentifierType ei = new ExternalIdentifierType();

        extIds.add(ei);
        ei.setRegistryObject(docId);
        ei.setIdentificationScheme(externalIdentifier.getIdentificationScheme());
        ei.setId(externalIdentifier.getIdentificationId());

        if (StringUtils.isNotBlank(externalIdentifier.getLocalizedString()))
        {
            InternationalStringType name = new InternationalStringType();
            List<LocalizedStringType> names = name.getLocalizedString();
            LocalizedStringType lname = new LocalizedStringType();
            lname.setValue(externalIdentifier.getLocalizedString());
            names.add(lname);
            ei.setName(name);
        }

        ei.setValue(value);
    }

    /**
     * Format a date using yyyyMMddHHmmss.
     * 
     * @param dateVal
     *            The date to format.
     * @return a formatted date object as a String.
     */
    protected String formatDate(Date dateVal)
    {
        final String formout = "yyyyMMddHHmmss";

        String ret = null;
        SimpleDateFormat dateOut = new SimpleDateFormat(formout);

        try
        {
            ret = dateOut.format(dateVal);
        }
        catch (Exception x)
        {
            x.printStackTrace();
        }

        return ret;
    }

    /**
     * Transform a String representation of a date from MM/dd/yyyy to
     * yyyyMMddHHmmss.
     * 
     * @param value
     *            The date as a String to transform.
     * @return the formatted date as a String.
     */
    protected String formatDateFromMDM(String value)
    {
        final String formin = "MM/dd/yyyy";
        final String formout = "yyyyMMddHHmmss";

        String ret = value;

        if (StringUtils.contains(value, "+"))
        {
            value = value.substring(0, value.indexOf("+"));
        }

        Date dateVal = null;
        SimpleDateFormat date = new SimpleDateFormat(formin);
        SimpleDateFormat dateOut = new SimpleDateFormat(formout);

        try
        {
            dateVal = date.parse(value);
            ret = dateOut.format(dateVal);
        }
        catch (Exception x)
        {
            x.printStackTrace();
        }

        return ret;
    }

    /**
     * Create a SlotType1 object using the given name and value.
     * 
     * @param name
     *            The slot name.
     * @param value
     *            The slot value.
     * @return a SlotType1 object.
     */
    protected SlotType1 makeSlot(String name, String value)
    {
        SlotType1 slot = new SlotType1();
        slot.setName(name);
        ValueListType values = new ValueListType();
        slot.setValueList(values);
        List<String> vals = values.getValue();
        vals.add(value);

        return slot;
    }

}
