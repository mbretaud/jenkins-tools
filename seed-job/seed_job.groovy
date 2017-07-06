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
    def token = "154ef662cf02c153c30dfd500de51f27258eff52"

    def conn = reposApi.openConnection()
//    conn.setRequestProperty("Authorization", "token ${token}")

//    def repos = new groovy.json.JsonSlurper().parseText(conn.content.text)

    println("reposUrl ${reposUrl}")

    return nextPage(conn);
}
