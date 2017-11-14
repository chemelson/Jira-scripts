// Fix resolutions corrupted with buggy ScriptRunner Bulk-fix resolutions script, which updates issue resolution date
// https://productsupport.adaptavist.com/browse/SRJIRA-2535

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.index.IssueIndexingService
import com.atlassian.jira.issue.search.SearchProvider
import com.atlassian.jira.jql.parser.JqlQueryParser
import com.atlassian.jira.web.bean.PagerFilter

def searchProvider = ComponentAccessor.getComponent(SearchProvider)
def issueIndexingService = ComponentAccessor.getComponent(IssueIndexingService)
def issueManager = ComponentAccessor.getIssueManager()
def loggedInUser = ComponentAccessor.getJiraAuthenticationContext()?.getLoggedInUser()
def jqlQueryParser = ComponentAccessor.getComponent(JqlQueryParser)

// replace here with the filter of issues you want to fix
def jql = "filter = 'Filter for SSPA'"

def query = jqlQueryParser.parseQuery(jql)

def pagerFilter = PagerFilter.newPageAlignedFilter(0, 1000)

def changeHistoryManager = ComponentAccessor.getChangeHistoryManager()

def results

while ((!results) || results.total > pagerFilter.getStart()) {
    results = searchProvider.search(query, loggedInUser, pagerFilter)

    results.issues.each { issue ->

        def changeItems = changeHistoryManager.getAllChangeItems(issue)

        if (changeItems) {
            def originalResolutionDate = changeItems.findAll {
                it.field == "resolution"
            }?.created.last()

            if (originalResolutionDate) {

                def mutableIssue = issueManager.getIssueObject(issue.key)

                mutableIssue.setResolutionDate(originalResolutionDate)

                // store old resolution date and reindex
                mutableIssue.store()
                issueIndexingService.reIndex(mutableIssue)
            }
        }
    }

    pagerFilter.setStart(pagerFilter.nextStart)
}

log.warn "Done"
