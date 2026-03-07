export default function handler(req, res) {
  res.json({ status: 'online', timestamp: new Date().toISOString() });
}
