import java.net.*;
import java.io.*;

/* PROXY SETTINGS */
System.getProperties().put("proxySet", "true");
System.getProperties().put("proxyHost", "10.54.64.5"); //TODO generate from env
System.getProperties().put("proxyPort", "8080"); //TODO generate from env

ignoredRepos = ["toto"]

next = repositoryJob("https://api.github.com/orgs/mbretaud/repos")
while(next != null) {
    next = repositoryJob(next)
}

def repositoryJob(reposUrl){
    def reposApi = new URL(reposUrl)
    def token = "34e023c7ee867359dab5ee078f355658cff3d274"

    def conn = reposApi.openConnection()
    conn.setRequestProperty("Authorization", "token ${token}")

    
    def repos = new groovy.json.JsonSlurper().parseText(conn.content.text)

    println("reposUrl ${reposUrl}")

    return nextPage(conn);
}

def nextPage(conn) {
    def link = conn.getHeaderField("Link")
    println(link)
    def next = null
    if(link != null) {
        def links = link.split(",")
        println(link)
        links.each {
            def pages = it.split(";")
            println(pages[1])
            if(pages.length >= 2 && pages[1].trim() == "rel=\"next\""){
                next = pages[0].substring(1, pages[0].length() - 1)
            }
        }
    }
    return next;
}

