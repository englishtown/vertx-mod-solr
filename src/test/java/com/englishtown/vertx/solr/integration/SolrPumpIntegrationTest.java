package com.englishtown.vertx.solr.integration;

import com.englishtown.vertx.solr.streams.impl.OffsetReadJsonStream;
import com.englishtown.vertx.solr.streams.impl.SolrPump;
import com.englishtown.vertx.solr.streams.impl.WriteJsonStreamBase;
import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

import static org.vertx.testtools.VertxAssert.*;

/**
 * Created by yongkim on 8/4/14.
 */
public class SolrPumpIntegrationTest extends SolrIntegrationTestBase {

    SolrPump solrPump;
    final List<JsonObject> results = new ArrayList<>();

    OffsetReadJsonStream offsetReadJsonStream;
    WriteJsonStreamBase writeJsonStreamBase;

    private void initJsonStreams(final Runnable onEnd, final Runnable onWrite) {

        writeJsonStreamBase = new WriteJsonStreamBase() {
            @Override
            public WriteJsonStreamBase write(JsonObject jsonObject) {
                results.add(jsonObject);
                if (onWrite != null) onWrite.run();
                return this;
            }

            @Override
            public WriteJsonStreamBase setWriteQueueMaxSize(int maxSize) {
                return this;
            }

            @Override
            public boolean writeQueueFull() {
                return false;
            }

            @Override
            public WriteJsonStreamBase handleEnd() {

                assertTrue(solrPump.objectsPumped() > 0);
                assertTrue(results.size() > 0);
                JsonObject result = results.get(0);
                assertNotNull(result);

                onEnd.run();
                testComplete();
                return this;
            }
        }.exceptionHandler(new Handler<Throwable>() {
            @Override
            public void handle(Throwable t) {
                handleThrowable(t);
                fail();
            }
        });

        offsetReadJsonStream = new OffsetReadJsonStream(query, serializer, vertx.eventBus(), address)
                .endHandler(new Handler<Void>() {
                    @Override
                    public void handle(Void aVoid) {
                        writeJsonStreamBase.handleEnd();
                    }
                })
                .exceptionHandler(new Handler<Throwable>() {
                    @Override
                    public void handle(Throwable t) {
                        handleThrowable(t);
                        fail();
                    }
                });

    }

    @Test
    public void testSolrPump_Start_Stop() {

        query.setQuery("name:*").setRows(5);

        initJsonStreams(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < results.size(); i++) {
                    // check each result for a docs array and ensure number_found is greater than 0
                    JsonArray docs = results.get(i).getArray("docs");
                    assertNotNull(docs);
                    Integer numberFound = results.get(i).getNumber("number_found").intValue();
                    assertTrue(numberFound > 0);
                }
            }
        }, null);

        solrPump = SolrPump.createPump(offsetReadJsonStream, writeJsonStreamBase);
        // start the pump, which initializes the dataHandler
        solrPump.start();

    }

    @Test
    public void testSolrPump_Pause_Resume() {

        initJsonStreams(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < results.size(); i++) {
                    // check each result for a docs array and ensure number_found is greater than 0
                    JsonArray docs = results.get(i).getArray("docs");
                    assertNotNull(docs);
                    Integer numberFound = results.get(i).getNumber("number_found").intValue();
                    assertTrue(numberFound > 0);
                }
            }
        }, new Runnable() {
            @Override
            public void run() {
                vertx.runOnContext(new Handler<Void>() {
                    @Override
                    public void handle(Void event) {
                        offsetReadJsonStream.resume();
                    }
                });
            }
        });

        query.setQuery("name:*").setRows(5);

        solrPump = SolrPump.createPump(offsetReadJsonStream, writeJsonStreamBase);
        solrPump.start();
        offsetReadJsonStream.pause();
        offsetReadJsonStream.resume();
        offsetReadJsonStream.pause();
        offsetReadJsonStream.resume();
        offsetReadJsonStream.pause();

    }

}