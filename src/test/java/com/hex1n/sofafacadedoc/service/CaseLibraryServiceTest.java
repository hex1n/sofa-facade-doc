package com.hex1n.sofafacadedoc.service;

import com.hex1n.sofafacadedoc.model.DocumentModel;
import com.hex1n.sofafacadedoc.store.StoreService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CaseLibraryServiceTest {
    @Test
    void createForcesRouteIdentityOverBodyIdentity() throws Exception {
        StoreService store = mock(StoreService.class);
        CaseLibraryService library = new CaseLibraryService(store);
        DocumentModel.SavedCase item = caseItem();
        item.id = 999;
        item.project = "other";
        item.branch = "other-branch";
        item.service = "OtherFacade";
        item.methodId = "other-method";
        when(store.saveCase(item)).thenReturn(42L);

        long id = library.create("loan", "feature/apply-flow", "LoanFacade", "submit-1", item);

        assertEquals(42L, id);
        ArgumentCaptor<DocumentModel.SavedCase> captor = ArgumentCaptor.forClass(DocumentModel.SavedCase.class);
        verify(store).saveCase(captor.capture());
        DocumentModel.SavedCase saved = captor.getValue();
        assertEquals(0, saved.id);
        assertEquals("loan", saved.project);
        assertEquals("feature/apply-flow", saved.branch);
        assertEquals("LoanFacade", saved.service);
        assertEquals("submit-1", saved.methodId);
    }

    @Test
    void updateForcesRouteProjectAndIdButKeepsEditableCaseFields() throws Exception {
        StoreService store = mock(StoreService.class);
        CaseLibraryService library = new CaseLibraryService(store);
        DocumentModel.SavedCase item = caseItem();
        item.id = 999;
        item.project = "other";
        item.branch = "feature/apply-flow";
        item.service = "LoanFacade";
        item.methodId = "submit-1";
        when(store.saveCase(item)).thenReturn(7L);

        long id = library.update("loan", 7L, item);

        assertEquals(7L, id);
        ArgumentCaptor<DocumentModel.SavedCase> captor = ArgumentCaptor.forClass(DocumentModel.SavedCase.class);
        verify(store).saveCase(captor.capture());
        DocumentModel.SavedCase saved = captor.getValue();
        assertEquals(7L, saved.id);
        assertEquals("loan", saved.project);
        assertEquals("feature/apply-flow", saved.branch);
        assertEquals("LoanFacade", saved.service);
        assertEquals("submit-1", saved.methodId);
        assertEquals("demo", saved.name);
    }

    @Test
    void deleteIsScopedToRouteProject() throws Exception {
        StoreService store = mock(StoreService.class);
        CaseLibraryService library = new CaseLibraryService(store);

        library.delete("loan", 7L);

        verify(store).deleteCase("loan", 7L);
    }

    @Test
    void nullCaseBodyIsBadRequest() {
        CaseLibraryService library = new CaseLibraryService(mock(StoreService.class));

        assertThrows(IllegalArgumentException.class, () -> library.create("loan", "main", "LoanFacade", "submit-1", null));
        assertThrows(IllegalArgumentException.class, () -> library.update("loan", 7L, null));
    }

    private DocumentModel.SavedCase caseItem() {
        DocumentModel.SavedCase item = new DocumentModel.SavedCase();
        item.name = "demo";
        item.note = "note";
        item.argsJson = "{\"args\":{}}";
        return item;
    }
}
