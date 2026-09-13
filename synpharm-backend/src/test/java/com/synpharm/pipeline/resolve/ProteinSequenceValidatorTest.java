package com.synpharm.pipeline.resolve;

import com.synpharm.exception.PredictionErrorCode;
import com.synpharm.exception.PredictionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProteinSequenceValidatorTest {

    private ProteinSequenceValidator validator;

    private static final String VALID_LONG_SEQUENCE =
            "MKWVTFISLLFLFSSAYSRGVFRRDTHKSEIAHRFKDLGEENFKALVLIAFAQYLQQCPFEDHVKLVNEVTEFAKTCVADESAENCDKSLHTLFGDKLCTVA" +
                    "TLRETYGEMADCCAKQEPERNECFLQHKDDNPNLPRLVRPEVDVMCTAFHDNEETFLKKYLYEIARRHPYFYAPELLFFAKRYKAAFTECCQAADKAACL" +
                    "LPKLDELRDEGKASSAKQRLKCASLQKFGERAFKAWAVARLSQRFPKAEFAEVSKLVTDLTKVHTECCHGDLLECADDRADLAKYICENQDSISSKLKEC" +
                    "CEKPLLEKSHCIAEVENDEMPADLPSLAADFVESKDVCKNYAEAKDVFLGMFLYEYARRHPDYSVVLLLRLAKTYETTLEKCCAAADPHECYAKVFDEFK" +
                    "PLVEEPQNLIKQNCELFEQLGEYKFQNALLVRYTKKVPQVSTPTLVEVSRSLGKVGSKCCKHPEAKRMPCAEDYLSVVLNQLCVLHEKTPVSDRVTKCCT" +
                    "ESLVNRRPCFSALEVDETYVPKEFNAETFTFHADICTLSEKERQIKKQTALVELVKHKPKATKEQLKAVMDDFAAFVEKCCKADDKETCFAEEGKKLVAAS" +
                    "QAALGL";

    @BeforeEach
    void setUp() {
        validator = new ProteinSequenceValidator();
    }

    @Test
    void 严格校验_合法长序列_返回去空白序列() {
        String result = validator.validateStrict(VALID_LONG_SEQUENCE + "  \n ");
        assertTrue(result.length() > ProteinSequenceValidator.MIN_SEQUENCE_LENGTH);
        assertFalse(result.contains(" "));
    }

    @Test
    void 严格校验_短序列_抛出SEQUENCE_TOO_SHORT() {
        PredictionException e = assertThrows(PredictionException.class,
                () -> validator.validateStrict("MGLGLGQ"));
        assertEquals(PredictionErrorCode.SEQUENCE_TOO_SHORT, e.getErrorCode());
    }

    @Test
    void 校验_含非法字符_抛出INVALID_SEQUENCE() {
        PredictionException e = assertThrows(PredictionException.class,
                () -> validator.validateLenient("MGLG123LG"));
        assertEquals(PredictionErrorCode.INVALID_SEQUENCE, e.getErrorCode());
    }

    @Test
    void 校验_空序列_抛出INVALID_SEQUENCE() {
        assertThrows(PredictionException.class, () -> validator.validateLenient("   "));
        assertThrows(PredictionException.class, () -> validator.validateLenient(null));
    }

    @Test
    void 宽松校验_短序列_通过() {
        // 存量兼容：smiles 输入类型下不强制最小长度
        assertEquals("MGLGLGQ", validator.validateLenient("MGLGLGQ"));
    }

    @Test
    void 校验_移除换行与空白() {
        assertEquals("MGLGLGQ", validator.validateLenient(" MGL\nGLGQ\t"));
    }
}
