package tests.buildTypes

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildFeatures.PullRequests
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.buildSteps.dockerCompose
import jetbrains.buildServer.configs.kotlin.buildSteps.script


object E2ETests : BuildType({
  name = "E2E tests"

  vcs {
    root(vcsRoots.KotlinLangOrg)
  git("Kotlin_KotlinSites_KotlinlangTeamcityDsl_BuildReferenceDocs") {
      name = "Kotlin_KotlinSites_KotlinlangTeamcityDsl_BuildReferenceDocs"
      url = "git@github.com:JetBrains/kotlin-web-site.git"
      branchSpec = """
        +:refs/heads/(add-testing-page)
    """.trimIndent()
  }
  }

    dependencies {
        artifacts(AbsoluteId("Kotlin_KotlinSites_KotlinlangTeamcityDsl_BuildReferenceDocs")) {
            cleanDestination = true
            artifactRules = "+:docs.zip!** => dist/"
        }
    }

  steps {
//      dockerCompose {
//          file = "docker-compose-e2e-statics.yml"
//      }
      script {
          scriptContent = "docker compose -f docker-compose-e2e-statics.yml up --exit-code-from playwright"
      }
  }

  requirements {
    exists("docker.server.version")
    contains("docker.server.osType", "linux")
  }

  features {
    pullRequests {
      vcsRootExtId = "${vcsRoots.KotlinLangOrg.id}"
      provider = github {
        authType = token {
          token = "%github.oauth%"
        }
        filterAuthorRole = PullRequests.GitHubRoleFilter.MEMBER_OR_COLLABORATOR
      }
    }
  }
})