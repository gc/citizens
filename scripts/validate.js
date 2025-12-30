const { readdirSync, readFileSync } = require("node:fs");
const path = require("node:path");

const BASE_PATH = "./src/main/resources/RegionData/";

function readJson(path) {
    return JSON.parse(readFileSync(path));
}

const npcModelIds = readJson("./scripts/npcs-models.json");
const files = readdirSync(BASE_PATH);
console.log({ files });

for (const file of files) {
    const { citizenRoster } = readJson(path.join(BASE_PATH, file));
    for (const citizen of citizenRoster) {
        for (const id of citizen.modelIds) {
            if (!npcModelIds.includes(id)) {
                console.log(`${citizen.name} has invalid model id: ${id}`)
            }
        }
    }
}
