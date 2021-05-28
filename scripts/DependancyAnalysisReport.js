const fs = require('fs');
const path = require('path');
const json2html = require('json2html');

let appDirName = process.env.ENV_APP_DIR_NAME;

const jsonReport = fs.readFileSync(path.resolve('../' + appDirName, 'dependency-analysis-report/report.json'), 'utf8');
var originalJSON = JSON.parse(jsonReport);
var prettifiedJSON = {};
prettifiedJSON['Unused Dependencies'] = originalJSON.dependencies;
prettifiedJSON['Unused Dev Dependencies'] = originalJSON.devDependencies;
prettifiedJSON['Missing Dependencies'] = originalJSON.missing;

var json2HTML = json2html.render(prettifiedJSON, {plainHtml: false});
var finalHTML = `<!DOCTYPE html>
                <html lang="en">
                    <head>
                        <meta charset="utf-8">
                        <title>Walgreens | Dependency Analysis Report.</title>
                    </head>
                    <body>
                        ${json2HTML}
                        <style>
                            #j2h .j2hexpand .index {
                                display: inline-block;
                                float: none;
                            }
                        </style>
                        
                    </body>
                </html>`
fs.writeFile(path.resolve('../' + appDirName, 'dependency-analysis-report/report.html'), finalHTML, 'utf8', function(error) {
    console.log('... HTML Report Generated Successfully ...');
    if (error) {
        console.log('HTML report creation error : ', error);
    }
});

