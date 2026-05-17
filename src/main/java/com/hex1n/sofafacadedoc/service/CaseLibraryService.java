package com.hex1n.sofafacadedoc.service;

import com.hex1n.sofafacadedoc.model.DocumentModel;
import com.hex1n.sofafacadedoc.store.StoreService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CaseLibraryService {
    private final StoreService store;

    public CaseLibraryService(StoreService store) {
        this.store = store;
    }

    public List<DocumentModel.SavedCase> list(String project, String branch, String service, String methodId) throws Exception {
        return store.listCases(project, branch, service, methodId);
    }

    public long create(String project, String branch, String service, String methodId, DocumentModel.SavedCase item) throws Exception {
        DocumentModel.SavedCase saved = requireCase(item);
        saved.id = 0;
        saved.project = project;
        saved.branch = branch;
        saved.service = service;
        saved.methodId = methodId;
        return store.saveCase(saved);
    }

    public long update(String project, long id, DocumentModel.SavedCase item) throws Exception {
        DocumentModel.SavedCase saved = requireCase(item);
        saved.id = id;
        saved.project = project;
        return store.saveCase(saved);
    }

    public void delete(String project, long id) throws Exception {
        store.deleteCase(project, id);
    }

    private DocumentModel.SavedCase requireCase(DocumentModel.SavedCase item) {
        if (item == null) throw new IllegalArgumentException("case is required");
        return item;
    }
}
