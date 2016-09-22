package commons.scripts

import java.util.Map
import java.util.Map.Entry
import java.util.regex.Matcher
import java.util.regex.Pattern;

import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.commons.httpclient.methods.PostMethod
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.vitalservice.VitalStatus;
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalsigns.model.GraphObject;
import ai.vital.vitalsigns.model.VITAL_GraphContainerObject;
import ai.vital.vitalsigns.model.VitalApp
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.S3Object
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Simple DocuSign api wrapper created to avoid long dependencies list/conflicts
 * @author Derek
 */

class DocuSignApiSimpleScript implements VitalPrimeGroovyScript {

	private final static Logger log = LoggerFactory.getLogger(DocuSignApiSimpleScript.class)
	
	static 	HttpClient client = new HttpClient()
	
	static Pattern s3URLPattern = Pattern.compile('^s3\\:\\/\\/([^\\/]+)\\/(.+)$', Pattern.CASE_INSENSITIVE)
	
	@Override
	public ResultList executeScript(VitalPrimeScriptInterface scriptInterface, Map<String, Object> params) {

		ResultList rl = new ResultList()
		
		try {
		
			String username = params.username
			if(!username) throw new Exception("No username param")
			
			String password = params.password
			if(!password) throw new Exception("No password param")
			
			String integratorKey = params.integratorKey
			if(!integratorKey) throw new Exception("No integratorKey param")
	
			String accountId = params.accountId
			if(!accountId) throw new Exception("No accountId")
			
			String action = params.action 
			if(!action) throw new Exception("No action param")

			if(action == 'send') {

				String email = params.email
				if(!email) throw new Exception("No email param")
				
				String name = params.name
				if(!name) throw new Exception("No name param")
	
				
				String base64Data = params.base64Data
				String s3URL = params.s3URL
				
				int i = 0
				
				if(base64Data) {
					i++
				} 
				if(s3URL) {
					i++
				}
				
				String fileName = null
				
				if(i == 0) {
					throw new Exception("One of base64Data, s3URL params required")
				}
				
				if(i > 1) {
					throw new Exception("Exactly one of base64Data, s3URL params required, got ${i}")
				}				
			
				if(base64Data) {
					
					fileName = params.fileName
					if(!fileName) throw new Exception("No fileName param")
					
				}	
			
				
				if(s3URL) {
					
					String s3AccessKey = params.get('s3AccessKey')
					String s3SecretKey = params.get('s3SecretKey')
					
					if(!s3AccessKey) throw new Exception("No s3AccessKey param, required with s3URL")
					if(!s3SecretKey) throw new Exception("No s3SecretKey param, required with s3URL")
					
					AmazonS3Client s3Client = new AmazonS3Client(new BasicAWSCredentials(s3AccessKey, s3SecretKey))
					
					Matcher m = s3URLPattern.matcher(s3URL)
					
					if( !m.matches() ) throw new Exception("invalid s3 url: ${s3URL} - must match ${s3URLPattern.pattern()}")
				
					InputStream inS = null
					ByteArrayOutputStream fos = null
					File f = null
					String key = m.group(2)
					String bucket = m.group(1)
					
					try {
						
						S3Object s3Object = s3Client.getObject(m.group(1), key)
						
						fileName = URLDecoder.decode(s3URL.substring(s3URL.lastIndexOf('/')), 'UTF-8')
						
						inS = s3Object.getObjectContent()
						
						fos = new ByteArrayOutputStream((int) s3Object.getObjectMetadata().contentLength)
						
						IOUtils.copy(inS, fos)
												
					} finally {
						IOUtils.closeQuietly(fos)
						IOUtils.closeQuietly(inS)
					}
					
					base64Data = Base64.getEncoder().encodeToString(fos.toByteArray())
					
//					String fname = e.getKey() 2
					
				}
				
				
				
				
				String dateSignedAnchor = params.dateSignedAnchor
				String signatureAnchor = params.signatureAnchor
//				if(!signatureAnchor) throw new Exception("No signatureAnchor param")
							
				PostMethod pm = new PostMethod("https://demo.docusign.net/restapi/v2/accounts/" + accountId + '/envelopes')
				
				// configure 'X-DocuSign-Authentication' authentication header
				String authHeader = "{\"Username\":\"" +  username + "\",\"Password\":\"" +  password + "\",\"IntegratorKey\":\"" +  integratorKey + "\"}";
				pm.addRequestHeader('X-DocuSign-Authentication', authHeader)
				pm.addRequestHeader('Accept', 'application/json')
						
				String ext = fileName.substring(fileName.lastIndexOf(".") + 1)
		
				Map r = [:]
				
						
				r.documents = [
					[
						documentBase64: base64Data,
						documentId: "1",
						fileExtension: ext,
						name: fileName
					]
				]
				
				r.emailSubject = "Please sign the Document"
				
				List dateSignedTabs = []
				if(dateSignedAnchor) {
					
					dateSignedTabs.add([
						//! always ignore
						anchorIgnoreIfNotPresent: 'true',
						anchorString: dateSignedAnchor,
						anchorXOffset: "0",
						anchorYOffset: "0",
//							fontSize: "Size12",
						name: "Signature Date",
						recipientId: "1",
						tabLabel: "signature_date"
					])
					
				}
				
				List signHereTabs = [
					[
						//! always ignore
						anchorIgnoreIfNotPresent: 'true',
						anchorString: signatureAnchor,
						anchorXOffset: "0",
						anchorYOffset: "0",
						name: "Signature",
						optional: "false",
						recipientId: "1",
//						scaleValue: 1,
						tabLabel: "signature"
					]
				] 
				
				r.recipients = [
					signers: [
						[
							email: email,
							name: name,
							recipientId: "1",
							routingOrder: "1",
							
							tabs: [
								dateSignedTabs: dateSignedTabs,
								/*
								fullNameTabs": [
										{
											"anchorString": "signer1name",
											"anchorYOffset": "-6",
											"fontSize": "Size12",
											"name": "Full Name",
											"recipientId": "1",
											"tabLabel": "Full Name"
										}
									],
								*/
								signHereTabs: signHereTabs
							]
						]
					]
				]
				
				r.status = "sent"
				
				/*
				{
					"documents": [
						{
							"documentBase64": "FILE1_BASE64",
							"documentId": "1",
							"fileExtension": "pdf",
							"name": "NDA.pdf"
						}
					],
					"emailSubject": "Please sign the NDA",
					"recipients": {
						"signers": [
							{
								"email": "the_nda_signer@mailinator.com",
								"name": "Darlene Petersen",
								"recipientId": "1",
								"routingOrder": "1",
								"tabs": {
									"dateSignedTabs": [
										{
											"anchorString": "signer1date",
											"anchorYOffset": "-6",
											"fontSize": "Size12",
											"name": "Date Signed",
											"recipientId": "1",
											"tabLabel": "date_signed"
										},
									],
									"fullNameTabs": [
										{
											"anchorString": "signer1name",
											"anchorYOffset": "-6",
											"fontSize": "Size12",
											"name": "Full Name",
											"recipientId": "1",
											"tabLabel": "Full Name"
										}
									],
									"signHereTabs": [
										{
											"anchorString": "signer1sig",
											"anchorUnits": "mms",
											"anchorXOffset": "0",
											"anchorYOffset": "0",
											"name": "Please sign here",
											"optional": "false",
											"recipientId": "1",
											"scaleValue": 1,
											"tabLabel": "signer1sig"
										}
									]
								}
							}
						]
					},
					"status": "sent"
				}
				*/
				
				
				pm.setRequestEntity(new StringRequestEntity(JsonOutput.toJson(r), 'application/json', 'UTF-8'))
						
				int status = client.executeMethod(pm)
				String res = ""
				try {
					res = pm.getResponseBodyAsString()
				} catch(Exception e) {
				}
				
				if(status < 200 || status > 299) throw new Exception("Http status: ${status} - ${res}")
				
				/*{
  "envelopeId": "d1cf4bea-1b78-47ea-b372-746678e3679f",
  "uri": "/envelopes/d1cf4bea-1b78-47ea-b372-746678e3679f",
  "statusDateTime": "2016-06-02T22:57:27.8300000Z",
  "status": "sent"
}*/
				
				Map parsed = new JsonSlurper().parseText(res)
				
				VITAL_GraphContainerObject o = new VITAL_GraphContainerObject().generateURI((VitalApp) null)
				
				for(Entry<String, Object> e : parsed.entrySet()) {
					o.setProperty(e.getKey(), e.getValue())
				}

				rl.addResult(o)				
				
			} else if(action == 'getstatus') {
			
				List<String> envelopeIds = params.envelopeIds
				if(envelopeIds == null) throw new Exception("No envelopeIds param")
				if(envelopeIds.size() == 0) throw new Exception("envelopeIds must not be empty") 
			 
				GetMethod pm = new GetMethod("https://demo.docusign.net/restapi/v2/accounts/" + accountId + '/envelopes/status?envelope_ids=' + envelopeIds.join(','))
				
				// configure 'X-DocuSign-Authentication' authentication header
				String authHeader = "{\"Username\":\"" +  username + "\",\"Password\":\"" +  password + "\",\"IntegratorKey\":\"" +  integratorKey + "\"}";
				pm.addRequestHeader('X-DocuSign-Authentication', authHeader)
				pm.addRequestHeader('Accept', 'application/json')
				
				Map r = [
					envelopeIds: envelopeIds
//    "transactionIds": [
//        "sample string 1"
//    ]
				]
				
//				pm.setRequestEntity(new StringRequestEntity(JsonOutput.toJson(r), 'application/json', 'UTF-8'))
				
				int status = client.executeMethod(pm)
				String res = ""
				try {
					res = pm.getResponseBodyAsString()
				} catch(Exception e) {
				}
				
				if(status < 200 || status > 299) throw new Exception("Http status: ${status} - ${res}")
				
				Map parsed = new JsonSlurper().parseText(res)
				
				for(Map<String, Object> env : parsed.envelopes) {
					VITAL_GraphContainerObject o = new VITAL_GraphContainerObject().generateURI((VitalApp) null)
					for(Entry<String, Object> e : env) {
						o.setProperty(e.getKey(), e.getValue())
					}
					rl.addResult(o)
					
				}
				
			} else if(action == 'getdocument') {
			
				String envelopeId = params.envelopeId
				if(!envelopeId) throw new Exception("No envelopeId param")
				
				GetMethod pm = new GetMethod("https://demo.docusign.net/restapi/v2/accounts/" + accountId + '/envelopes/' + envelopeId + '/documents/1')
				// configure 'X-DocuSign-Authentication' authentication header
				String authHeader = "{\"Username\":\"" +  username + "\",\"Password\":\"" +  password + "\",\"IntegratorKey\":\"" +  integratorKey + "\"}";
				pm.addRequestHeader('X-DocuSign-Authentication', authHeader)
//				pm.addRequestHeader('Accept', 'application/json')
				
				int status = client.executeMethod(pm)
				
				if(status < 200 || status > 299) {

					String res = ""
					try {
						res = pm.getResponseBodyAsString()
					} catch(Exception e) {
					}
										
					throw new Exception("Http status: ${status} - ${res}")
				}
				
				String contents = Base64.getEncoder().encodeToString(pm.getResponseBody())
				
				VITAL_GraphContainerObject o = new VITAL_GraphContainerObject().generateURI((VitalApp) null)
				o.setProperty('base64Data', contents)
				rl.addResult(o)
			
				
			} else if(action == 'cancel') {
			
				String envelopeId = params.envelopeId
				if(!envelopeId) throw new Exception("No envelopeId param")
				
				PutMethod pm = new PutMethod("https://demo.docusign.net/restapi/v2/accounts/" + accountId + '/envelopes/' + envelopeId)
				// configure 'X-DocuSign-Authentication' authentication header
				String authHeader = "{\"Username\":\"" +  username + "\",\"Password\":\"" +  password + "\",\"IntegratorKey\":\"" +  integratorKey + "\"}";
				pm.addRequestHeader('X-DocuSign-Authentication', authHeader)
//				pm.addRequestHeader('Accept', 'application/json')
				
				Map r = [
					"status":"voided", 
					"voidedReason": "Cancelled by the broker"
				]
				
				pm.setRequestEntity(new StringRequestEntity(JsonOutput.toJson(r), 'application/json', 'UTF-8'))
				
				int status = client.executeMethod(pm)
				
				if(status < 200 || status > 299) {

					String res = ""
					try {
						res = pm.getResponseBodyAsString()
					} catch(Exception e) {
					}
										
					throw new Exception("Http status: ${status} - ${res}")
				}
				
				
				rl.status = VitalStatus.withOKMessage("Envelope voided: ${envelopeId}")
					
			} else {
				throw new Exception("Unknown action: ${action}")
			}
			
			
			
		} catch(Exception e) {
			log.error(e.localizedMessage, e)
			rl.status = VitalStatus.withError(e.localizedMessage)
		}
		
		return rl
			
	}

}
