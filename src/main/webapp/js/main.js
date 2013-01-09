// Global Variables
var jBugUser = "";
var jBugUserMD5 = "";
var mainWindowHeight = 100;
var bugList = "";
var currentPage = 1;
var winW = 630, winH = 460;

function checkUser()
{
    jBugUser = $.cookie("jbug.useremail");

    if (jBugUser === null)
    {
        getUserEmail();
    } else {
        jBugUserMD5 = $.md5(jBugUser);
        updateUserGravatar();
    }
}

function getUserEmail()
{
    if (jBugUser === null)
        jBugUser = "";
    jBugUser = prompt("What's your email?", window.jBugUser);
    jBugUserMD5 = $.md5(jBugUser);
    $.cookie("jbug.useremail", jBugUser);
    updateUserGravatar();
}

function updateUserGravatar()
{
    $("#grv").html("<img align='top' src='http://www.gravatar.com/avatar/" + jBugUserMD5 + "?s=28'/>");
}

function getUserGravatarImg(email, size)
{
    if (size === null || size === undefined)
        size = 28;
    return "<img align='top' style='width:" + size + "px;height:" + size + "px;' src='http://www.gravatar.com/avatar/" + $.md5(email) + "?d=wavatar&s=" + size + "'/>";
}

function setMainContentHeight()
{
    if (document.body && document.body.offsetWidth) {
        winW = document.body.offsetWidth;
        winH = document.body.offsetHeight;
    }
    if (document.compatMode === 'CSS1Compat' &&
            document.documentElement &&
            document.documentElement.offsetWidth) {
        winW = document.documentElement.offsetWidth;
        winH = document.documentElement.offsetHeight;
    }
    if (window.innerWidth && window.innerHeight) {
        winW = window.innerWidth;
        winH = window.innerHeight;
    }
    mainWindowHeight = winH - 10 - 60;
    $("#main").height(mainWindowHeight + "px");
    showCurrentBugPage();
}

function updateBugStatusBar()
{
    $.ajax({
        url: "/data.jsp?get=openbugcount",
        context: document.body
    }).done(function(data) {
        $("#openbugs").html(data);
    });
}

var buglist = "";

function showOpenBugs()
{
    $("#main").html("<img src='/img/pcman.gif'/>");

    $.ajax({
        url: "/data.jsp?get=openbugids",
        context: document.body
    }).done(function(data) {
        bugList = data;
        showBugList();
    });
}

function showBugList()
{
    currentPage = 1;
    showCurrentBugPage();
}

function pageLink(page, isCurrent)
{
    html="<a href='javascript:currentPage="+page+";showCurrentBugPage();' class='"+((isCurrent===true)?"currentpg":"navpg")+"'>"+((page<10)?"&nbsp;":"")+page+"</a>";
    return html;
}

function showCurrentBugPage()
{
    if (bugList === undefined || bugList.length === 0 || bugList === "Not found")
        return;

    bugs = bugList.split(",");
    pageSize = Math.floor((mainWindowHeight - 70) / 33);
    nPages = Math.ceil(bugs.length / pageSize);

    navBar = "<br/><center> ";
    start=0;
    regions=[ [1,3], [currentPage-3,currentPage+3], [nPages-2,nPages] ];
    
    for(z=0;z<regions.length-1;z++)
        {
            if (regions[z][1]>regions[z+1][0]){
                regions[z][1]=regions[z+1][1];
                for(k=z+1;k<regions.length-1;k++) regions[k]=regions[k+1];
                regions.splice(regions.length-1,1);
                z--;
            }
        }
    
    for(z=0;z<regions.length;z++)
    {
        if (z!==0) navBar+="&nbsp;&nbsp;&nbsp;...&nbsp;&nbsp;&nbsp;";
        for(a=regions[z][0];a<=regions[z][1];a++) navBar+=pageLink(a, currentPage===a);
    }

//    for(i=0;i!==nPages;i++)
//        navBar+=" "+(i+1);
    navBar += "</center><br/>";

    firstBug = (currentPage - 1) * pageSize;

    // Make a bug list
    ids = "";
    for (i = firstBug; i < bugs.length && i < firstBug + pageSize; i++) {
        if (ids.length > 0)
            ids += ",";
        ids += bugs[i];
    }

    $.ajax({
        url: "/data.jsp?get=bugssummaries&for=" + ids,
        async: false,
        context: document.body
    }).done(function(data) {
        json = data;
    });

    if (json === "Not found")
    {
        $("#main").html("No bugs found");
    }

    bugs = JSON.parse(json).bugs;
    table = "<table><tr class='titlerow'><td style='width:28px;'>#</td><td style='width:28px;'>Rep</td><td style='width:28px;'>As2</td><td>Sev</td><td>Pri</td><td>Summary</td></tr>";
    for (i = 0; i !== bugs.length; i++)
        table += getBugSummaryRow(i + firstBug + 1, bugs[i], ((i % 2) === 0) ? "buglight" : "bugdark");
    table += "</table>";
    $("#main").html(navBar + table);
}

function getSeverityName(severity)
{
    switch (severity)
    {
        case "0":
            return "Blocker";
        case "1":
            return "Critical";
        case "2":
            return "Major";
        case "3":
            return "Minor";
        case "4":
            return "Trivial";
    }
}

function getBugSummaryRow(num, bug, color)
{
    if (color === undefined)
        color = "white";
    row = "<tr class='bugsummaryrow " + color + "'>" +
            "<td><b>" + num + "</b></td>" +
            "<td>" + getUserGravatarImg(bug.REPORTER) + "</td>" +
            "<td>" + getUserGravatarImg(bug.ASSIGNED_TO) + "</td>" +
            "<td>" + getSeverityName(bug.SEVERITY) + "</td>" +
            "<td>P" + (parseInt(bug.PRIORITY) + 1) + "</td>" +
            "<td class='bugsummarydesctd' style='max-width:" + (winW - 200) + "px'><b>" + bug.TITLE + "</b><span class='summarydesc'> - " + bug.DESCRIPTION + "</span></td>";

    row += "</tr>";
    return row;
}