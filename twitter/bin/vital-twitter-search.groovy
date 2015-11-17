/*   CONFIG PARAMS   */

def SERVICE_PROFILE = 'primelocal'
def SERVICE_KEY     = 'serv-serv-serv'

def QUERY = args[0]

def TWITTER_KEY = 'key'
def TWITTER_SECRET = 'secret'

def COUNT = 10
def GEOCODE = null
/*
[
	lat: 0.0d,
	lon: 0.0d,
	radius: 100,
	unit: 'mi'
]
*/
def LANG = 'en'
def LOCALE = null
def MAX_ID = null //
def RESULT_TYPE = 'mixed'// 'popular' 'recent
def SINCE = null//
def SINCE_ID = null
def UNTIL = null

/*   END OF CONFIG   */



import ai.vital.vitalsigns.model.VitalServiceKey
import ai.vital.vitalservice.factory.VitalServiceFactory
import com.vitalai.domain.social.Tweet

//necessary, as groovy shell does not 
ai.vital.vitalsigns.VitalSigns.get().registerOntology(new com.vitalai.domain.nlp.ontology.Ontology(), 'vital-nlp-groovy-0.2.300.jar')
ai.vital.vitalsigns.VitalSigns.get().registerOntology(new com.vitalai.domain.social.ontology.Ontology(), 'vital-social-groovy-0.2.300.jar')


def vitalService = VitalServiceFactory.openService(new VitalServiceKey(key: SERVICE_KEY), SERVICE_PROFILE)

def scriptParams = [
	//type is constant
	searchType: 'tweets',
	key: TWITTER_KEY,
	secret: TWITTER_SECRET,
	count: COUNT,
	geoCode: GEOCODE,
	lang: LANG,
	locale: LOCALE,
	maxId: MAX_ID,
	query: QUERY,
	resultType: RESULT_TYPE,
	since: SINCE,
	sinceId: SINCE_ID,
	until: UNTIL
]

def resultList = vitalService.callFunction('commons/scripts/TwitterSearchScript.groovy', scriptParams)

println "STATUS: ${resultList.status}"

println "TOTAL RESULTS: ${resultList.totalResults}"

int i = 0

for(Tweet tweet : resultList) {
	
	i++
	
	long tweetID = tweet.tweetID.longValue()
	
	long authorID = tweet.authorID.longValue()
	
	Date pubDate = tweet.publicationDate.getDate()
	
	String body = tweet.body
	
	String authorName = tweet.authorName
	
	println "${i}. ${pubDate} ${authorName}: ${body}"
	
}



