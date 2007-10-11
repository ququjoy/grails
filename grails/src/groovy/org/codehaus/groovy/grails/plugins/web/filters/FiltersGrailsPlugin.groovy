/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.grails.plugins.web.filters
import org.codehaus.groovy.grails.plugins.web.filters.FiltersConfigArtefactHandler
import org.codehaus.groovy.grails.plugins.web.filters.FilterToHandlerAdapter
import org.apache.commons.logging.LogFactory
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.springframework.beans.factory.config.MethodInvokingFactoryBean
import org.codehaus.groovy.grails.web.plugins.support.WebMetaUtils

/**
 * @author Mike
 * @author Graeme Rocher
 *
 * @since 1.0
 *
 * Created: Oct 10, 2007
 */
class FiltersGrailsPlugin {
    private static TYPE = FiltersConfigArtefactHandler.TYPE

    def version = 0.1
    def dependsOn = [:]
    def artefacts = [ FiltersConfigArtefactHandler ]
    def watchedResources = "file:./grails-app/conf/*Filters.groovy"
    def log = LogFactory.getLog(FiltersGrailsPlugin)

    static final BEANS = { filter ->
            "${filter.fullName}Class"(MethodInvokingFactoryBean) {
                targetObject = ref("grailsApplication", true)
                targetMethod = "getArtefact"
                arguments = [TYPE, filter.fullName]
            }
            "${filter.fullName}"(filter.clazz) { bean ->
                bean.singleton = true
                bean.autowire = "byName"
            }
    }
	def doWithSpring = {
		filterInterceptor(org.codehaus.groovy.grails.plugins.web.filters.CompositeInterceptor) {

        }

        for(filter in application.getArtefacts(TYPE)) {
            def callable = BEANS.curry(filter)
            callable.delegate = delegate
            callable.call()
        }
	}

	def doWithDynamicMethods = {
        for(filter in application.getArtefacts(TYPE)) {
            def mc = filter.metaClass
            WebMetaUtils.registerCommonWebProperties(mc, application)                            
        }
    }
	def doWithApplicationContext = { applicationContext ->
        reloadFilters(application, applicationContext)
	}
	def onChange = { event ->
	    if (log.isDebugEnabled()) log.debug("onChange: ${event}")
        def newFilter = event.application.addArtefact(TYPE, event.source)
        def ctx = event.ctx
        ctx.removeBeanDefinition("${newFilter.fullName}Class")
        ctx.removeBeanDefinition("${newFilter.fullName}")
        beans(BEANS.curry(newFilter)).registerBeans(event.ctx)
        
        reloadFilters(event.application, event.ctx)
	}

    private reloadFilters(GrailsApplication application, applicationContext) {
        log.info "reloadFilters"
        def filterConfigs = application.getArtefacts(TYPE)
        def handlers = []
        for(c in filterConfigs) {
            def filterClass = applicationContext.getBean("${c.fullName}Class")
            def bean = applicationContext.getBean(c.fullName)
            for(filterConfig in filterClass.getConfigs(bean)) {
                handlers << new FilterToHandlerAdapter(filterConfig:filterConfig, configClass:bean)
            }
        }

        if (log.isDebugEnabled()) log.debug("resulting handlers: ${handlers}")
        applicationContext.getBean('filterInterceptor').handlers = handlers
    }
}