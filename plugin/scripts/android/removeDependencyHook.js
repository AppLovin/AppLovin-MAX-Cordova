const fs = require('fs/promises');
const path = require('path');
const { dependencyText } = require('./dependencyText');

module.exports = async function(context) {
    const rootBuildGradlePath = path.join(context.opts.projectRoot, 'platforms/android/build.gradle');
    let handler;

    console.log('AppLovin remove dependency hook');

    try {
        const text = await fs.readFile(rootBuildGradlePath, 'utf8');

        if (text.indexOf(dependencyText) > -1) {
            const textWithoutAppLovinDependency = text.replace(dependencyText, '');

            await fs.writeFile(rootBuildGradlePath, textWithoutAppLovinDependency);
        }
    } catch (error) {
        console.error('AppLovin remove dependency hook error', error);
    } finally {
        handler && fs.close(handler);
        console.log('AppLovin remove dependency completed');
    }
}