const fs = require('fs/promises');
const path = require('path');
const { dependencyText } = require('./dependencyText');

module.exports = async function(context) {
    const rootBuildGradlePath = path.join(context.opts.projectRoot, 'platforms/android/build.gradle');
    let handler;

    console.log('AppLovin add dependency hook');

    try {
        const handler = await fs.open(rootBuildGradlePath, 'a+');

        const text = await handler.readFile('utf8');

        if (text.indexOf(dependencyText) === -1) {
            await handler.write(dependencyText);
        }
    } catch (error) {
        console.error('AppLovin add dependency hook error', error);
        throw 'AppLovin add dependency hook error';
    } finally {
        handler && fs.close(handler);
        console.log('AppLovin add dependency hook completed');
    }
}