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
    conn.setRequestProperty("Authorization", "token ${token}")

    def repos = new groovy.json.JsonSlurper().parseText(conn.content.text)

    repos.each {
        def repo = it.name
        def contentsUrl = it.contents_url
        def project = it.full_name
        def gitUrl = it.ssh_url

        if(ignoredRepos.contains(repo)) {
            println("${project} is ignored")
        } else {
            println("${project} on pulls")
            nextPr = createJobPr(repo, "https://api.github.com/repos/${project}/pulls", true, contentsUrl, gitUrl, token)
            while(nextPr != null) {
                nextPr = createJobPr(repo, nextPr, true, contentsUrl, gitUrl, token)
            }


            println("${project} on branches")
            nextBr = createJobBranch(repo, "https://api.github.com/repos/${project}/branches", false, contentsUrl, gitUrl, token)
            while(nextBr != null) {
                nextBr = createJobBranch(repo, nextBr, false, contentsUrl, gitUrl, token)
            }
        }
    }

    return nextPage(conn);
}

/**
 *
 * @param url pull or branch
 * @param type PR or branc
 * @return
 */
def createJobPr(repo, url, pr, contentsUrl, gitUrl, token){
    //listing branches
    def branchesApi = new URL(url)

    def branchesConn = branchesApi.openConnection()
    branchesConn.setRequestProperty("Authorization", "token ${token}")

    def branches = new groovy.json.JsonSlurper().parseText(branchesConn.content.text)

    branches.each {
        def branch = it
        def branchName = branch.head.ref
        def htmlLink = branch.html_url

        createJob(repo, contentsUrl, branchName, pr, htmlLink, gitUrl, token)

    }
    return nextPage(branchesConn)
}

/**
 *
 * @param url pull or branch
 * @param type PR or branc
 * @return
 */
def createJobBranch(repo, url, pr, contentsUrl, gitUrl, token){
    //listing branches
    def branchesApi = new URL(url)

    def branchesConn = branchesApi.openConnection()
    branchesConn.setRequestProperty("Authorization", "token ${token}")

    def branches = new groovy.json.JsonSlurper().parseText(branchesConn.content.text)

    branches.each {
        def branch = it
        def branchName = branch.name
        createJob(repo, contentsUrl, branchName, pr, "", gitUrl, token)
    }
    return nextPage(branchesConn)
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


def createJob(repo, contentsUrl, branchName, isAPr, htmlLink, gitUrl, token) {
    //TODO refactoring with BRANCH
    if( branchName != null && ! branchName.startsWith("release/") ) {

        def type = isAPr ? "PR" : "BRANCH"

        if (branchName == 'develop') {
            jobName = "${repo}-DEVELOP".replaceAll('[^A-Za-z0-9_-]', '_')
        } else {
            jobName = "${repo}-${branchName}-${type}".replaceAll('[^A-Za-z0-9_-]', '_')
        }

        def jenkinsFile = jenkinsFileContent(contentsUrl, branchName, token)

        if (jenkinsFile) {
            def dependsOn = itDependsOn(jenkinsFile)

            println("job ${jobName} depends on ${dependsOn}")

            def jobNameToUSe = jobName
            workflowJob(jobNameToUSe) {
                definition {
                    cpsScm {
                        scm {
                            git {
                                remote {
                                    url(gitUrl)
                                }
                                branch("${branchName}")
                            }
                        }
                        scriptPath('Jenkinsfile')
                    }
                }
                triggers {
                    scm("H/5 * * * *")
                    if (dependsOn)
                        upstream(dependsOn, 'SUCCESS')
                }
                environmentVariables {
                    loadFilesFromMaster(true)
                    env('GIT_BRANCH', branchName)
                    env('isApr', isAPr)
                    env('PR_LINK', htmlLink)
                    env('REPO', repo)
                }
                logRotator(30, 5, -1, -1)
            }
        }
    }
}

def jenkinsFileContent(contentsUrl, branche, token) {
    def c = contentsUrl.replaceAll('\\{\\+path\\}', "Jenkinsfile?ref=${branche}")
    println("contents url ${c}")
    def jenkinsFile = new URL(c)

    try {
        def conn = jenkinsFile.openConnection()
        conn.setRequestProperty("Authorization", "token ${token}")

        def fileApiResponse = new groovy.json.JsonSlurper().parseText(conn.content.text)

        def encodedContent = fileApiResponse.content
        return new String(encodedContent.decodeBase64())
    } catch (java.io.FileNotFoundException fie) {
        println("missing JenkinsFile on job ${c}")
        return ""
    }
}

def itDependsOn(content) {
    def result = ""
    def regex = /.*\@dependOn.*/

    content.eachLine {
        println(it)
        if ((it =~ regex).matches()) {
            result = (it.substring(it.lastIndexOf("@dependOn") + 10))
        }
    }
    return result
}
