const fs = require('fs/promises');
const path = require('path');
var exec = require('child_process').exec;

const scriptName = 'applovinQualityServiceSetup.rb';

async function execApplovinScript(path) {
    return new Promise((resolve, reject) => {
        exec(`cd ${path} && ruby ${scriptName}`, function (error, stdout, stderr) {
            console.log('stdout: ' + stdout);
            console.log('stderr: ' + stderr);

            if (error !== null) {
                console.error('exec error: ' + error);
                reject(error);
            } else {
                resolve();
            }
        });
    });
}

module.exports = async function(context) {
    const iosPath = path.join(context.opts.projectRoot, 'platforms/ios');

    try {
        console.log('Applovin copying quality service setup script');
        await fs.copyFile(scriptName, `${iosPath}/${scriptName}`);
        console.log('Applovin running setup script');
        await execApplovinScript(iosPath);
        console.log('Applovin removing setup script');
        await fs.rm(`${iosPath}/${scriptName}`);
        console.log('Applovin script completed');
    } catch (error) {
        console.error('Applovin setup script error', error);
        throw 'Applovin setup script error';
    }
}