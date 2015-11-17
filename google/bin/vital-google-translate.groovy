/*   CONFIG PARAMS   */

def SERVICE_PROFILE = 'primelocal'
def SERVICE_KEY     = 'serv-serv-serv'

def TARGET_LANG = args[0]
def INPUT_FILE = args[1]
def OUTPUT_FILE = args[2]

def GOOGLE_TRANSLATE_API_KEY = 'key'

def FORMAT = 'text' //'html'

/*   END OF CONFIG   */

import java.lang.annotation.Documented;

import ai.vital.vitalsigns.VitalSigns;
import ai.vital.vitalsigns.block.BlockCompactStringSerializer;
import ai.vital.vitalsigns.block.BlockCompactStringSerializer.BlockIterator;
import ai.vital.vitalsigns.block.BlockCompactStringSerializer.VitalBlock;
import ai.vital.vitalsigns.model.GraphObject;
import ai.vital.vitalsigns.model.VitalApp;
import ai.vital.vitalsigns.model.VitalServiceKey
import ai.vital.vitalservice.VitalStatus;
import ai.vital.vitalservice.factory.VitalServiceFactory

import com.vitalai.domain.nlp.Document
import com.vitalai.domain.social.Tweet


File inputFile = new File(INPUT_FILE)

println "Input file: ${inputFile.absolutePath}"
println "Target language: ${TARGET_LANG}"

boolean pretty = ( OUTPUT_FILE == '-print' || OUTPUT_FILE == '--print') 

File outputFile = null

if(!pretty) {
	
	outputFile = new File(OUTPUT_FILE)
	
	println "Output file: ${outputFile.absolutePath}"
	
	if(outputFile.exists()) {
		System.err.println("Output file already exists: ${outputFile.absolutePath}")
		return
	}
	
	
} else {

	println "Printing results to the console"	

}

BlockCompactStringSerializer writer = outputFile != null ? new BlockCompactStringSerializer(outputFile) : null

//necessary, as groovy shell does not 
ai.vital.vitalsigns.VitalSigns.get().registerOntology(new com.vitalai.domain.nlp.ontology.Ontology(), 'vital-nlp-groovy-0.2.300.jar')
ai.vital.vitalsigns.VitalSigns.get().registerOntology(new com.vitalai.domain.social.ontology.Ontology(), 'vital-social-groovy-0.2.300.jar')


def vitalService = VitalServiceFactory.openService(new VitalServiceKey(key: SERVICE_KEY), SERVICE_PROFILE)

int i = 0
int docs = 0

for( BlockIterator iterator = BlockCompactStringSerializer.getBlocksIterator(inputFile); iterator.hasNext(); ) {

	i++
	
	if(writer != null) {
		writer.startBlock()
	}
	
	for( GraphObject g : iterator.next().toList() ) {

		if(g instanceof Document) {
			
			docs++
			
			Document document = g;
			String body = document.body?.toString()
			
			if(! body ) {
				if(writer != null) {
					writer.writeGraphObject(g)
				}
				println "Document with URI: ${document.URI} has no body property - skipping"
				continue;
			}
			
			boolean autodetect = document.lang == null
			
			def scriptParams = [
				//type is constant
				key: GOOGLE_TRANSLATE_API_KEY,
				format: FORMAT,
				target: TARGET_LANG,
				document: document
			]
			
			def resultList = vitalService.callFunction('commons/scripts/GoogleTranslateScript.groovy', scriptParams)
			
			if(resultList.status.status != VitalStatus.Status.ok) {
				
				if(writer != null) {
					writer.writeGraphObject(g)
				}
				
				println "Document URI: ${document.URI} status: ${resultList.status}"

				continue				
			}
			
			boolean docFound = false
			
			for(GraphObject r : resultList) {
				
				if(document.URI == r.URI) {
					//refresh it
					document = (Document)r
					docFound = true
				}
				
				if(writer != null) {
					writer.writeGraphObject(r)
				}
				
				VitalSigns.get().addToCache(r)
				
			}
			
			if(!docFound) {
				println "ERROR: no document found in the output results"
				continue
			}
			
			List<Document> translations = document.getTranslations()
			
			if(translations.size() < 1) {
				println "ERROR: no translations found"
			}
			
			if(outputFile != null) continue
			
			println "Document URI: ${document.URI}"
			println "Source text: ${document.body}"
			if(autodetect) {
				println "Detected language: ${document.lang}"
			} else {
				println "Source language: ${document.lang}"
			}
			
			if(translations.size() > 0) {
			}
			
			for(Document translation : translations) {
				println "Translation [${translation.lang}]: \n${translation.body}"
			}
			
			println ""
			
			
		} else {
		
			if(writer != null) {
				
				writer.writeGraphObject(g)
				
			} else {
				println "Ignoring object of type: ${g.getClass().canonicalName}, URI: ${g.URI}" 
			}
			
		
		}	
		
		
	}

	if(writer != null) {
		writer.endBlock()
	}	
	
	
}

if(writer != null) {
	writer.close()
}

println "DONE, blocks iterated: ${i}, documents iterated: ${docs}"


