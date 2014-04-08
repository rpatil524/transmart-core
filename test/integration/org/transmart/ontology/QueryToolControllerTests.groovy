package org.transmart.ontology

import grails.test.mixin.TestMixin
import groovy.json.JsonSlurper
import org.gmock.WithGMock
import org.junit.Before
import org.junit.Test
import org.springframework.web.context.request.RequestContextHolder
import org.transmartproject.core.querytool.QueriesResource
import org.transmartproject.core.querytool.QueryDefinition
import org.transmartproject.core.querytool.QueryDefinitionXmlConverter
import org.transmartproject.core.querytool.QueryResult
import org.transmartproject.core.querytool.QueryStatus
import org.transmartproject.db.test.RuleBasedIntegrationTestMixin

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.*

@TestMixin(RuleBasedIntegrationTestMixin)
@WithGMock
class QueryToolControllerTests {

    QueryToolController testee

    @Before
    void before() {
        testee = new QueryToolController()
    }

    @Test
    void testRunQueryFromDefinition() {
        def queryDefinition = new QueryDefinition([])
        def resultInstance = new QueryResult() {
            Long id = -1
            Long setSize = 10
            QueryStatus status = QueryStatus.FINISHED
            String errorMessage = null
            Set patients = [] as Set
        }

        QueryDefinitionXmlConverter xmlService = mock(QueryDefinitionXmlConverter)
        xmlService.fromXml(anyOf(any(Reader), nullValue())).
                returns queryDefinition

        QueriesResource queriesService = mock(QueriesResource)
        queriesService.runQuery(is(queryDefinition)).returns resultInstance

        testee.queryDefinitionXmlService = xmlService
        testee.queriesResourceService = queriesService

        play {
            testee.runQueryFromDefinition()
        }

        def response = RequestContextHolder.requestAttributes.currentResponse
        assertThat response.contentType, startsWith('application/json')

        def json = new JsonSlurper().parseText(response.text)
        assertThat json, allOf(
                hasEntry('errorMessage', null),
                hasEntry('id', -1),
                hasEntry('setSize', 10),
                hasEntry('status', 'FINISHED'),
        )
    }
}
