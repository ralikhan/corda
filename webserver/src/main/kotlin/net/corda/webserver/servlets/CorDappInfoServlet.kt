package net.corda.webserver.servlets

import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.CordaPluginRegistry
import org.glassfish.jersey.server.model.Resource
import org.glassfish.jersey.server.model.ResourceMethod
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Dumps some data about the installed CorDapps.
 * TODO: Add registered flow initiators.
 */
class CorDappInfoServlet(val plugins: List<CordaPluginRegistry>, val rpc: CordaRPCOps): HttpServlet() {

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        resp.writer.appendHTML().html {
            head {
                title { +"Installed CorDapps" }
            }
            body {
                h2 { +"Installed CorDapps" }
                plugins.forEach { plugin ->
                    h3 { +plugin::class.java.name }
                    if (plugin.requiredFlows.isNotEmpty()) {
                        div {
                            p { +"Whitelisted flows:" }
                            ul {
                                plugin.requiredFlows.map { it.key }.forEach { li { +it } }
                            }
                        }
                    }
                    if (plugin.webApis.isNotEmpty()) {
                        div {
                            plugin.webApis.forEach { api ->
                                val resource = Resource.from(api.apply(rpc)::class.java)
                                p { +"${resource.name}:" }
                                val endpoints = processEndpoints("", resource, mutableListOf<Endpoint>())
                                ul {
                                    endpoints.forEach {
                                        li { a(it.uri) { +"${it.method}\t${it.text}" } }
                                    }
                                }
                            }
                        }
                    }
                    if (plugin.staticServeDirs.isNotEmpty()) {
                        div {
                            p { +"Static web content:" }
                            ul {
                                plugin.staticServeDirs.map { it.key }.forEach {
                                    li { a("web/$it") { +it } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    data class Endpoint(val method: String, val uri: String, val text: String)

    /**
     * Recursively enumerate and record all of the end-points listed in the API implementations.
     */
    private fun processEndpoints(uriPrefix: String, resource: Resource, endpoints: MutableList<Endpoint>): List<Endpoint> {
        val resources = arrayListOf<Resource>()
        val path = if (resource.path != null) "$uriPrefix/${resource.path}" else uriPrefix

        resources.addAll(resource.childResources)

        for (method in resource.allMethods) {
            if (method.type == ResourceMethod.JaxrsType.SUB_RESOURCE_LOCATOR) {
                resources.add( Resource.from(resource.resourceLocator.invocable.definitionMethod.returnType))
            } else {
                endpoints.add(Endpoint(method.httpMethod, "api$path", resource.path))
            }
        }

        resources.forEach {
            processEndpoints(path, it, endpoints)
        }

        return endpoints
    }
}
