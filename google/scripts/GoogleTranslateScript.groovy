package commons.scripts

import groovy.json.JsonSlurper;

import java.util.Map;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.io.IOUtils;

import com.vitalai.domain.nlp.Document;
import com.vitalai.domain.nlp.Edge_hasTranslation;

import ai.vital.prime.groovy.VitalPrimeGroovyScript;
import ai.vital.prime.groovy.VitalPrimeScriptInterface;
import ai.vital.vitalservice.VitalStatus;
import ai.vital.vitalservice.query.ResultElement
import ai.vital.vitalservice.query.ResultList;
import ai.vital.vitalsigns.model.VitalApp

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
			
			List<NameValuePair> params = [
				new NameValuePair('key', key),
				new NameValuePair('format', format),
				new NameValuePair('prettyprint', 'true'),
				new NameValuePair('q', body),
				new NameValuePair('target', target)
			]
			
			if(lang) {
				params.add(new NameValuePair('source', lang))
			}
			
			postMethod.setRequestBody(params as NameValuePair[])
			
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
