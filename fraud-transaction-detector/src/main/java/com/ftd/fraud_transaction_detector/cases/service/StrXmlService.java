package com.ftd.fraud_transaction_detector.cases.service;

import com.ftd.fraud_transaction_detector.cases.entity.CaseRecord;
import com.ftd.fraud_transaction_detector.transactions.entity.Transaction;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
public class StrXmlService {
    public byte[] generate(CaseRecord caseRecord, Transaction transaction, String generatedBy) {
        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element root = document.createElement("SuspiciousTransactionReport");
            root.setAttribute("version", "1.0");
            root.setAttribute("generatedAt", Instant.now().toString());
            document.appendChild(root);

            Element metadata = append(document, root, "ReportMetadata", null);
            append(document, metadata, "CaseNumber", caseRecord.getCaseNo());
            append(document, metadata, "FraudAlertId", value(caseRecord.getFraudAlertId()));
            append(document, metadata, "GeneratedBy", generatedBy);
            append(document, metadata, "ReportType", "DRAFT_STR");

            Element caseDetails = append(document, root, "CaseDetails", null);
            append(document, caseDetails, "Title", caseRecord.getTitle());
            append(document, caseDetails, "Priority", caseRecord.getPriority());
            append(document, caseDetails, "AssignedTo", value(caseRecord.getAssignedTo()));
            append(document, caseDetails, "OriginalCreatedAt", value(caseRecord.getCreatedAt()));

            Element subject = append(document, root, "Subject", null);
            append(document, subject, "AccountId", transaction.getAccountId());
            append(document, subject, "CustomerAge", value(transaction.getCustomerAge()));
            append(document, subject, "CustomerOccupation", value(transaction.getCustomerOccupation()));

            Element transactionElement = append(document, root, "Transaction", null);
            append(document, transactionElement, "TransactionId", transaction.getTransactionId());
            append(document, transactionElement, "Amount", value(transaction.getTransactionAmount()));
            append(document, transactionElement, "TransactionType", transaction.getTransactionType());
            append(document, transactionElement, "TransactionDate", value(transaction.getTransactionDate()));
            append(document, transactionElement, "Location", transaction.getLocation());
            append(document, transactionElement, "Channel", transaction.getChannel());
            append(document, transactionElement, "LoginAttempts", value(transaction.getLoginAttempts()));
            append(document, transactionElement, "AccountBalance", value(transaction.getAccountBalance()));
            append(document, transactionElement, "SourceType", transaction.getSourceType());

            Element declaration = append(document, root, "Declaration", null);
            append(document, declaration, "Statement",
                    "This is a system-generated draft for analyst review and is not a jurisdiction-specific regulatory filing.");

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true);
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate STR XML", exception);
        }
    }

    private static Element append(Document document, Element parent, String name, String content) {
        Element element = document.createElement(name);
        if (content != null) element.setTextContent(content);
        parent.appendChild(element);
        return element;
    }

    private static String value(Object value) { return value == null ? "" : value.toString(); }
}
