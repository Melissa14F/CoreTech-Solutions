package co.coretech.portal.comunicacion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import co.coretech.portal.ContenedoresPrueba;

/**
 * RNF-13 solo con la cola de respaldo (sin el envío inmediato): es el peor caso, si el envío inmediato fallara. La cola
 * pasa cada 15 segundos (el valor de application.yml), así que ningún correo debería esperar mucho más que eso.
 */
@SpringBootTest(properties = {
        "coretech.correos.inmediato=false",
        "coretech.correos.cron=*/15 * * * * *"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class MedicionCorreosColaTest extends MedicionCorreosBase {

    @Override
    String modo() {
        return "cola";
    }

    @Test
    void el95PorCientoDeLosCorreosSaleEnMenosDeUnMinutoSoloConLaCola() throws Exception {
        medir();
    }
}
