package com.synpharm.pipeline.resolve;

import com.synpharm.exception.PredictionErrorCode;
import com.synpharm.exception.PredictionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PredictionInputResolverTest {

    @Mock
    private UniProtResolver uniProtResolver;

    @Mock
    private PdbResolver pdbResolver;

    private PredictionInputResolver resolver;

    private static final String VALID_SEQUENCE = "MKWVTFISLLFLFSSAYSRGVFRRDTHKSEIAHRFKDLGEENFKALVLIAFAQYLQQCPFEDHVKLVNEVTEFAK";

    @BeforeEach
    void setUp() {
        resolver = new PredictionInputResolver(uniProtResolver, pdbResolver, new ProteinSequenceValidator());
    }

    @Test
    void DTI_smiles类型_第二参数为序列_按序列宽松校验() {
        ResolvedPredictionInput input = resolver.resolve("DTI", "smiles", "CCO," + VALID_SEQUENCE);
        assertEquals("CCO", input.getLigandSmiles());
        assertEquals(VALID_SEQUENCE, input.getTargetSequence());
        verifyNoInteractions(uniProtResolver, pdbResolver);
    }

    @Test
    void DTI_smiles类型_第二参数为UniProtID_自动嗅探并解析() {
        when(uniProtResolver.resolve("P0DTC2")).thenReturn(VALID_SEQUENCE);
        ResolvedPredictionInput input = resolver.resolve("DTI", "smiles", "CCO,P0DTC2");
        assertEquals(VALID_SEQUENCE, input.getTargetSequence());
        verify(uniProtResolver).resolve("P0DTC2");
        verifyNoInteractions(pdbResolver);
    }

    @Test
    void DTI_smiles类型_第二参数为PDB引用_自动嗅探并解析() {
        // looksLikePdbRef / isUniProtId 为静态方法，测试走真实逻辑
        when(pdbResolver.resolve("6M0J:A")).thenReturn(VALID_SEQUENCE);
        ResolvedPredictionInput input = resolver.resolve("DTI", "smiles", "CCO,6M0J:A");
        assertEquals(VALID_SEQUENCE, input.getTargetSequence());
        verify(pdbResolver).resolve("6M0J:A");
        verify(uniProtResolver, never()).resolve(anyString());
    }

    @Test
    void DTI_uniprot类型_显式解析() {
        when(uniProtResolver.resolve("P12345")).thenReturn(VALID_SEQUENCE);
        ResolvedPredictionInput input = resolver.resolve("DTI", "uniprot", "CCO,P12345");
        assertEquals(VALID_SEQUENCE, input.getTargetSequence());
        verify(uniProtResolver).resolve("P12345");
    }

    @Test
    void DTI_pdb类型_显式解析_默认链A() {
        when(pdbResolver.resolve("6M0J")).thenReturn(VALID_SEQUENCE);
        ResolvedPredictionInput input = resolver.resolve("DTI", "pdb", "CCO,6M0J");
        assertEquals(VALID_SEQUENCE, input.getTargetSequence());
        verify(pdbResolver).resolve("6M0J");
    }

    @Test
    void PPI_uniprot类型_两个ID分别解析() {
        when(uniProtResolver.resolve("P00533")).thenReturn(VALID_SEQUENCE);
        when(uniProtResolver.resolve("P12345")).thenReturn(VALID_SEQUENCE + "AAA");
        ResolvedPredictionInput input = resolver.resolve("PPI", "uniprot", "P00533,P12345");
        assertEquals(VALID_SEQUENCE, input.getProteinA());
        assertEquals(VALID_SEQUENCE + "AAA", input.getProteinB());
    }

    @Test
    void DDI_smiles类型_校验通过() {
        ResolvedPredictionInput input = resolver.resolve("DDI", "smiles", "CC(=O)OC1=CC=CC=C1C(=O)O,C1CCCCC1");
        assertEquals("CC(=O)OC1=CC=CC=C1C(=O)O", input.getDrugA());
        assertEquals("C1CCCCC1", input.getDrugB());
    }

    @Test
    void DDI_uniprot类型_拒绝() {
        PredictionException e = assertThrows(PredictionException.class,
                () -> resolver.resolve("DDI", "uniprot", "P12345,P23456"));
        assertEquals(PredictionErrorCode.INPUT_RESOLVE_FAILED, e.getErrorCode());
    }

    @Test
    void SMILES括号不配平_抛出INVALID_SMILES() {
        PredictionException e = assertThrows(PredictionException.class,
                () -> resolver.resolve("DDI", "smiles", "C(,CC"));
        assertEquals(PredictionErrorCode.INVALID_SMILES, e.getErrorCode());
    }

    @Test
    void 输入缺少逗号_抛出INPUT_RESOLVE_FAILED() {
        PredictionException e = assertThrows(PredictionException.class,
                () -> resolver.resolve("DTI", "smiles", "CCO"));
        assertEquals(PredictionErrorCode.INPUT_RESOLVE_FAILED, e.getErrorCode());
    }

    @Test
    void 未知算法类型_抛出INPUT_RESOLVE_FAILED() {
        PredictionException e = assertThrows(PredictionException.class,
                () -> resolver.resolve("XXX", "smiles", "A,B"));
        assertEquals(PredictionErrorCode.INPUT_RESOLVE_FAILED, e.getErrorCode());
    }
}
