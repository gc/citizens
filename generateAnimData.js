const fs = require('fs');
const path = require('path');

// Path to AnimationID.java
const animationIDFilePath = './src/main/java/com/magnaboy/AnimationID.java';

// Check if AnimationID.java exists
if (!fs.existsSync(animationIDFilePath)) {
    throw new Error(`File ${animationIDFilePath} does not exist.`);
}

// Read AnimationID.java
const animationIDFile = fs.readFileSync(animationIDFilePath, 'utf8');

// Match all the IDs from the file
const ids = animationIDFile.match(/(\d+)/g);

const animData = {};

// Iterate through each ID, read the corresponding JSON file, and push it into the array
ids.forEach((id) => {
    const filePath = `./dump/sequences/${id}.json`;
    if (!fs.existsSync(filePath)) {
        throw new Error(`File ${filePath} does not exist.`);
    }
    const jsonContent = JSON.parse(fs.readFileSync(filePath, 'utf8'));
    animData[id] = {
        id: jsonContent.id,
        frameCount: jsonContent.frameIDs.length,
        clientTicks: jsonContent.frameLenghts.reduce((a, b) => a + b, 0),
    };

    animData[id].realDurationMillis = animData[id].clientTicks * 20;
});

// Write the array to a .json file
fs.writeFileSync('./src/main/resources/animationData.json', JSON.stringify(animData, null, 2));
