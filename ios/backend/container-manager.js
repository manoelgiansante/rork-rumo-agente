const { execFileSync, exec } = require("child_process");
const crypto = require("crypto");

const DOCKER_IMAGE = "rumo-desktop";
const CONTAINER_PREFIX = "rumo-desktop-";
const BASE_VNC_PORT = 5901;
const BASE_NOVNC_PORT = 6100;
const MAX_CONTAINERS = 15;
const INACTIVITY_TIMEOUT_MS = 30 * 60 * 1000; // 30 min

// Track active containers: userId -> { port, noVncPort, lastActivity, containerId }
const activeContainers = new Map();

// Port pool: 6100-6114 for noVNC, 5901-5915 for VNC
function getAvailablePorts() {
  for (let i = 0; i < MAX_CONTAINERS; i++) {
    const noVncPort = BASE_NOVNC_PORT + i;
    const vncPort = BASE_VNC_PORT + i;
    const inUse = [...activeContainers.values()].some(
      (c) => c.noVncPort === noVncPort,
    );
    if (!inUse) return { vncPort, noVncPort };
  }
  return null;
}

function containerName(userId) {
  // Use first 12 chars of userId for container name
  return CONTAINER_PREFIX + userId.replace(/-/g, "").substring(0, 12);
}

// Safe exec helper - never interpolates user input into shell
function dockerExec(...args) {
  try {
    return execFileSync("docker", args, {
      encoding: "utf-8",
      timeout: 30000,
    }).trim();
  } catch (err) {
    return null;
  }
}

// Generate secure random VNC password per container
function generateVncPassword() {
  return crypto.randomBytes(12).toString("base64url").substring(0, 16);
}

function getContainerStatus(userId) {
  const name = containerName(userId);
  const status = dockerExec("inspect", "-f", "{{.State.Status}}", name);
  return status; // 'running', 'exited', 'paused', or null
}

async function startDesktop(userId) {
  const name = containerName(userId);
  const existing = getContainerStatus(userId);

  // If container already running, just update activity
  if (existing === "running") {
    const info = activeContainers.get(userId);
    if (info) {
      info.lastActivity = Date.now();
      return {
        status: "running",
        noVncPort: info.noVncPort,
        vncPort: info.vncPort,
      };
    }
    // Container running but not tracked - get ports from docker
    const ports = dockerExec("port", name, "6080");
    if (ports) {
      const noVncPort = parseInt(ports.split(":").pop());
      const vncPorts = dockerExec("port", name, "5901");
      const vncPort = vncPorts ? parseInt(vncPorts.split(":").pop()) : 5901;
      activeContainers.set(userId, {
        noVncPort,
        vncPort,
        lastActivity: Date.now(),
        containerId: name,
      });
      return { status: "running", noVncPort, vncPort };
    }
  }

  // If container exists but stopped, remove it (ports may have changed)
  if (existing === "exited") {
    dockerExec("rm", name);
  }

  // Check container limit
  if (activeContainers.size >= MAX_CONTAINERS) {
    // Try to stop least recently active
    const oldest = [...activeContainers.entries()].sort(
      (a, b) => a[1].lastActivity - b[1].lastActivity,
    )[0];
    if (oldest) {
      await stopDesktop(oldest[0]);
    }
  }

  // Get available ports
  const ports = getAvailablePorts();
  if (!ports) {
    throw new Error(
      "Sem recursos disponíveis. Tente novamente em alguns minutos.",
    );
  }

  // Generate unique VNC password for this container
  const vncPassword = generateVncPassword();

  // Start new container using safe execFileSync
  const containerId = dockerExec(
    "run",
    "-d",
    "--name",
    name,
    "--hostname",
    "rumo-desktop",
    "--memory=1g",
    "--cpus=0.5",
    "--shm-size=256m",
    "-p",
    `${ports.vncPort}:5901`,
    "-p",
    `${ports.noVncPort}:6080`,
    "-e",
    `VNC_PASSWORD=${vncPassword}`,
    "--restart",
    "unless-stopped",
    DOCKER_IMAGE,
  );
  if (!containerId) {
    throw new Error("Falha ao iniciar desktop. Tente novamente.");
  }

  activeContainers.set(userId, {
    noVncPort: ports.noVncPort,
    vncPort: ports.vncPort,
    vncPassword: vncPassword,
    lastActivity: Date.now(),
    containerId: name,
  });

  // Wait for desktop to be ready
  await new Promise((resolve) => setTimeout(resolve, 3000));

  return {
    status: "starting",
    noVncPort: ports.noVncPort,
    vncPort: ports.vncPort,
  };
}

async function stopDesktop(userId) {
  const name = containerName(userId);
  dockerExec("stop", name);
  dockerExec("rm", name);
  activeContainers.delete(userId);
  return { status: "stopped" };
}

async function takeScreenshot(userId) {
  const name = containerName(userId);
  const status = getContainerStatus(userId);
  if (status !== "running") return null;

  // Update activity
  const info = activeContainers.get(userId);
  if (info) info.lastActivity = Date.now();

  // Take screenshot inside container
  const result = dockerExec(
    "exec",
    name,
    "bash",
    "-c",
    "export DISPLAY=:1 && scrot -o /tmp/screenshot.png 2>/dev/null && echo ok",
  );

  if (result !== "ok") return null;

  // Copy screenshot out
  const screenshotPath = `/tmp/screenshot-${name}.png`;
  dockerExec("cp", `${name}:/tmp/screenshot.png`, screenshotPath);

  return screenshotPath;
}

function getDesktopInfo(userId) {
  const status = getContainerStatus(userId);
  const info = activeContainers.get(userId);

  return {
    desktop: status === "running",
    status: status || "stopped",
    noVncPort: info?.noVncPort || null,
    lastActivity: info?.lastActivity || null,
  };
}

// Cleanup inactive containers every 5 minutes
setInterval(
  () => {
    const now = Date.now();
    for (const [userId, info] of activeContainers.entries()) {
      if (now - info.lastActivity > INACTIVITY_TIMEOUT_MS) {
        console.log(
          `Auto-stopping inactive desktop for user ${userId.substring(0, 8)}...`,
        );
        stopDesktop(userId).catch((err) =>
          console.error("Auto-stop error:", err),
        );
      }
    }
  },
  5 * 60 * 1000,
);

// On startup, sync with existing containers
function syncContainers() {
  try {
    const output = dockerExec(
      "ps",
      "--filter",
      `name=${CONTAINER_PREFIX}`,
      "--format",
      "{{.Names}} {{.Ports}}",
    );
    if (!output) return;

    output
      .split("\n")
      .filter(Boolean)
      .forEach((line) => {
        const parts = line.split(" ");
        const name = parts[0];
        const userId = name.replace(CONTAINER_PREFIX, "");

        // Parse ports from docker ps output
        const portsStr = parts.slice(1).join(" ");
        const noVncMatch = portsStr.match(/0\.0\.0\.0:(\d+)->6080/);
        const vncMatch = portsStr.match(/0\.0\.0\.0:(\d+)->5901/);

        if (noVncMatch) {
          activeContainers.set(userId, {
            noVncPort: parseInt(noVncMatch[1]),
            vncPort: vncMatch ? parseInt(vncMatch[1]) : 5901,
            lastActivity: Date.now(),
            containerId: name,
          });
          console.log(`Synced container ${name} (noVNC: ${noVncMatch[1]})`);
        }
      });
  } catch (err) {
    console.error("Container sync error:", err);
  }
}

syncContainers();

module.exports = {
  startDesktop,
  stopDesktop,
  takeScreenshot,
  getDesktopInfo,
  getContainerStatus,
  activeContainers,
};
