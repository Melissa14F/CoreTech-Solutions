package co.coretech.portal.comunicacion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import co.coretech.portal.ContenedoresPrueba;

/** RNF-13 con el envío inmediato (lo normal en producción): el correo sale en cuanto se guarda el aviso. */
@SpringBootTest(properties = "coretech.correos.inmediato=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class MedicionCorreosInmediatosTest extends MedicionCorreosBase {

    @Override
    String modo() {
        return "inmediato";
    }

    @Test
    void el95PorCientoDeLosCorreosSaleEnMenosDeUnMinuto() throws Exception {
        medir();
    }
}
