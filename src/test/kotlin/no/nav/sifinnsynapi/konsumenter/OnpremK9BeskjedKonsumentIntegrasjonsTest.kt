package no.nav.sifinnsynapi.konsumenter

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.sifinnsynapi.config.Topics.DITT_NAV_BESKJED
import no.nav.sifinnsynapi.config.Topics.K9_DITTNAV_VARSEL_BESKJED_ONPREM
import no.nav.sifinnsynapi.utils.*
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.concurrent.TimeUnit

@EmbeddedKafka( // Setter opp og tilgjengligjør embeded kafka broker
    topics = [K9_DITTNAV_VARSEL_BESKJED_ONPREM, DITT_NAV_BESKJED],
    count = 3,
    bootstrapServersProperty = "kafka-servers" // Setter bootstrap-servers for consumer og producer.
)
@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // Integrasjonstest - Kjører opp hele Spring Context med alle konfigurerte beans.
class OnpremK9BeskjedKonsumentIntegrasjonsTest {

    @Autowired
    lateinit var mapper: ObjectMapper

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private lateinit var embeddedKafkaBroker: EmbeddedKafkaBroker // Broker som brukes til å konfigurere opp en kafka producer.

    lateinit var producer: Producer<String, Any> // Kafka producer som brukes til å legge på kafka meldinger
    lateinit var dittNavConsumer: Consumer<Nokkel, Beskjed> // Kafka consumer som brukes til å lese kafka meldinger.

    @BeforeAll
    fun setUp() {
        producer = embeddedKafkaBroker.opprettKafkaProducer()
        dittNavConsumer = embeddedKafkaBroker.opprettDittnavConsumer()
    }

    @AfterAll
    internal fun tearDown() {
        producer.close()
        dittNavConsumer.close()
    }

    @Test
    fun `Legger K9Beskjed på topic og forvent publisert dittnav beskjed`() {
        // legg på 1 hendelse om mottatt søknad
        val k9Beskjed = gyldigK9Beskjed(
            tekst = "Vi har mottatt din søknad om pleiepenger - sykt barn. Klikk under for mer info.",
            link = "https://www.nav.no"
        )

        producer.leggPåTopic(k9Beskjed, K9_DITTNAV_VARSEL_BESKJED_ONPREM, mapper)

        // forvent at mottatt hendelse konsumeres og at det blir sendt ut en beskjed på aapen-brukernotifikasjon-nyBeskjed-v1 topic
        val brukernotifikasjon = dittNavConsumer.hentBrukernotifikasjon(k9Beskjed.eventId)?.value()
        validerRiktigBrukernotifikasjon(k9Beskjed, brukernotifikasjon)
    }

    @Test
    fun `Dersom K9Beskjed link er null forventes det at publisert dittnav  Beskjed link er tom`() {
        // legg på 1 hendelse om mottatt søknad med link = null...
        val k9Beskjed = gyldigK9Beskjed(
            tekst = "Vi har mottatt omsorgspengesøknad fra deg om å bli regnet som alene om omsorgen for barn.",
            link = null
        )
        producer.leggPåTopic(k9Beskjed, K9_DITTNAV_VARSEL_BESKJED_ONPREM, mapper)

        // forvent at mottatt hendelse konsumeres og at det blir sendt ut en beskjed på aapen-brukernotifikasjon-nyBeskjed-v1 topic
        val brukernotifikasjon = dittNavConsumer.hentBrukernotifikasjon(k9Beskjed.eventId)?.value()
        validerRiktigBrukernotifikasjon(k9Beskjed, brukernotifikasjon)
        assertTrue(brukernotifikasjon?.getLink() == "")
    }

}

fun validerRiktigBrukernotifikasjon(k9Beskjed: K9Beskjed, brukernotifikasjon: Beskjed?){
    assertTrue(brukernotifikasjon != null)
    assertTrue(k9Beskjed.tekst == brukernotifikasjon?.getTekst())
    assertTrue(k9Beskjed.søkerFødselsnummer == brukernotifikasjon?.getFodselsnummer())
    assertTrue(k9Beskjed.grupperingsId == brukernotifikasjon?.getGrupperingsId())
    println("BRUKERNOTIFIKASJON VALIDERT OK")
}