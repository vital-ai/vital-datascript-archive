package commons.scripts

import groovy.json.JsonSlurper

import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods.PostMethod
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity
import org.apache.commons.httpclient.methods.multipart.Part
import org.apache.commons.httpclient.methods.multipart.StringPart
import org.apache.commons.io.IOUtils

import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.vitalservice.VitalStatus
import ai.vital.vitalservice.query.ResultElement
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalsigns.model.VitalApp

import com.vitalai.domain.nlp.Document
import com.vitalai.domain.nlp.Edge_hasTranslation

class GoogleTranslateScript implements VitalPrimeGroovyScript {

	private static HttpClient client = new HttpClient()
	
	@Override
	public ResultList executeScript(
			VitalPrimeScriptInterface scriptInterface,
			Map<String, Object> parameters) {

		ResultList rl = new ResultList()
		
		InputStream inputStream = null
		
		PostMethod postMethod = null
		
		try {
			
			String key = parameters.get('key')
			if(!key) throw new Exception("No 'key' parameter")
			
			String format = parameters.get('format')
			if(!format) throw new Exception("No 'format' parameter")
			
			String target = parameters.get('target')
			if(!target) throw new Exception("No 'target' parameter")
			
			Object document = parameters.get("document")
			if(document == null) throw new Exception("No 'document' parameter")
			if(!(document instanceof Document)) throw new Exception("'document' must be an instance of ${Document.class.canonicalName}")
			
			String body = document.body?.toString()
			if(!body) throw new Exception("'document' must have body defined")
			
			String lang = document.lang?.toString()

			postMethod = new PostMethod("https://www.googleapis.com/language/translate/v2")
			postMethod.addRequestHeader("X-HTTP-Method-Override", "GET")
			postMethod.addRequestHeader("Accept-Charset", "UTF-8")
			
			List<Part> parts = [
				new StringPart("key", key, "UTF-8"),
				new StringPart('format', format, "UTF-8"),
				new StringPart('prettyprint', 'true', "UTF-8"),
				new StringPart('q', body, "UTF-8"),
				new StringPart('target', target, "UTF-8")
			]

			if(lang) {
				parts.add(
					new StringPart('source', lang, "UTF-8")
				) 
			}			
			
			//https://groups.google.com/forum/#!topic/google-translate-api/5ChxNAOCrGI
//			List<NameValuePair> params = [
//				new NameValuePair('key', key),
//				new NameValuePair('format', format),
//				new NameValuePair('prettyprint', 'true'),
//				new NameValuePair('q', body),
//				new NameValuePair('target', target)
//			]
//			
//			if(lang) {
//				params.add(new NameValuePair('source', lang))
//			}
//			postMethod.setRequestBody(params as NameValuePair[])
			
			postMethod.setRequestEntity(new MultipartRequestEntity(parts as Part[], postMethod.getParams()))
			
			int status = client.executeMethod(postMethod)
			if(status != 200) {
				String resp = ""
				try { resp = postMethod.getResponseBodyAsString(10000) } catch(Exception e) {}
				throw new Exception("Google Translate API HTTP status: ${status} - ${resp}")
			}
			
			inputStream = postMethod.getResponseBodyAsStream()
			
			JsonSlurper slurper = new JsonSlurper()
			
			Map jsonRes = slurper.parse(inputStream)
			
			List translations = ((Map)jsonRes.data).translations
			
//			{
//				"data": {
//					"translations": [
//						{
//							"translatedText": "Hallo Welt"
//						},
//						{
//							"translatedText": "Mein Name ist Jeff"
//						}
//					]
//				}
//			}
			
			if(translations.size() != 1) throw new Exception("Expected exactly one translation, got: ${translations.size()}")
			
			Map translationObj = translations[0]
			
			Document translation = new Document().generateURI((VitalApp)null)
			translation.body = translationObj.translatedText
			translation.lang = target
			
			if(!lang) {
				document.lang = translationObj.detectedSourceLanguage 
			}
			
			Edge_hasTranslation edge = new Edge_hasTranslation().addSource(document).addDestination(translation).generateURI((VitalApp) null)
			rl.results.add(new ResultElement(document, 1D))
			rl.results.add(new ResultElement(edge, 2D))
			rl.results.add(new ResultElement(translation, 3D))
			
		} catch(Exception e) {
			rl.status = VitalStatus.withError("Google Translate API failed: ${e.localizedMessage}")
		} finally {
			IOUtils.closeQuietly(inputStream)
			if(postMethod != null) postMethod.releaseConnection()
		}
		
		return rl
	}
	
	

}
