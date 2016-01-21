package org.fastcatsearch.job.indexing;

import org.fastcatsearch.common.io.Streamable;
import org.fastcatsearch.exception.FastcatSearchException;
import org.fastcatsearch.http.action.service.indexing.IndexDocumentsAction;
import org.fastcatsearch.http.action.service.indexing.JSONRequestReader;
import org.fastcatsearch.http.action.service.indexing.MapDocument;
import org.fastcatsearch.ir.CollectionDynamicIndexer;
import org.fastcatsearch.ir.IRService;
import org.fastcatsearch.ir.config.CollectionContext;
import org.fastcatsearch.ir.config.DataInfo;
import org.fastcatsearch.ir.io.DataInput;
import org.fastcatsearch.ir.io.DataOutput;
import org.fastcatsearch.ir.search.CollectionHandler;
import org.fastcatsearch.job.Job;
import org.fastcatsearch.service.ServiceManager;
import org.fastcatsearch.util.CollectionContextUtil;
import org.fastcatsearch.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created by swsong on 2016. 1. 14..
 */
public class NodeIndexDocumentFileJob extends Job implements Streamable {

    private String collectionId;
    private String documentId;
    private String documents;

    public NodeIndexDocumentFileJob() {
    }

    public NodeIndexDocumentFileJob(String collectionId, String documentId, String documents) {
        this.collectionId = collectionId;
        this.documentId = documentId;
        this.documents = documents;
    }

    @Override
    public JobResult doRun() throws FastcatSearchException {
        try {
            IRService irService = ServiceManager.getInstance().getService(IRService.class);
            CollectionHandler collectionHandler = irService.collectionHandler(collectionId);
            CollectionDynamicIndexer indexer = null;
            try {
                indexer = new CollectionDynamicIndexer(documentId, collectionHandler);

                List<MapDocument> jsonList = new JSONRequestReader().readMapDocuments(documents);
                for (MapDocument doc : jsonList) {
                    String type = String.valueOf(doc.getType());
                    Map<String, Object> sourceMap = doc.getSourceMap();

                    if (type.equals(IndexDocumentsAction.INSERT_TYPE)) {
                        indexer.insertDocument(sourceMap);
                    } else if (type.equals(IndexDocumentsAction.UPDATE_TYPE)) {
                        indexer.updateDocument(sourceMap);
                    } else if (type.equals(IndexDocumentsAction.DELETE_TYPE)) {
                        indexer.deleteDocument(sourceMap);
                    }
                }
            } finally {
                DataInfo.SegmentInfo segmentInfo = null;
                if (indexer != null) {
                    segmentInfo = indexer.close();
                }

                File segmentDir = indexer.getSegmentDir();
                if(segmentInfo.getInsertCount() == 0 && segmentInfo.getUpdateCount() == 0) {
                    logger.info("[{}] Delete segment dir due to no documents = {}", collectionHandler.collectionId(), segmentDir.getAbsolutePath());
                    FileUtils.deleteDirectory(segmentDir);
                    //세그먼트를 삭제하고 없던 일로 한다.
                    segmentInfo = null;
                }

                CollectionContext collectionContext = collectionHandler.applyNewSegment(segmentInfo, segmentDir, indexer.getDeleteIdSet());
                CollectionContextUtil.saveCollectionAfterIndexing(collectionContext);

            }
        } catch (Exception e) {
            logger.error("node dynamic index error!", e);
        }

        return new JobResult();
    }

    @Override
    public void readFrom(DataInput input) throws IOException {
        collectionId = input.readString();
        documentId = input.readString();
        documents = input.readString();
    }

    @Override
    public void writeTo(DataOutput output) throws IOException {
        output.writeString(collectionId);
        output.writeString(documentId);
        output.writeString(documents);
    }
}
