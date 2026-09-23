let pass = 0, fail = 0;
const eq = (name, got, want) => {
  const ok = JSON.stringify(got) === JSON.stringify(want);
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}`);
  ok ? pass++ : fail++;
};
const inRoll = (r, d) => (!r.from || d >= r.from) && (!r.to || d <= r.to);
const cell = (M, d, sid, h) => { const s = (M[d] || {})[sid]; return s ? (s[h] || '-') : '-'; };
function totals(days, roster, marks) {
  const out = {};
  roster.forEach(r => out[r.sid] = { p: 0, od: 0, a: 0, h: 0 });
  Object.keys(days).sort().filter(d => days[d].on).forEach(d => {
    if (!days[d].done) return;
    roster.forEach(r => {
      if (!inRoll(r, d)) return;
      for (let k = 0; k < days[d].hours; k++) {
        const v = cell(marks, d, r.sid, k);
        if (v === '-') continue;
        out[r.sid].h++;
        if (v === 'P') out[r.sid].p++;
        else if (v === 'O') { out[r.sid].p++; out[r.sid].od++; }
        else out[r.sid].a++;
      }
    });
  });
  return out;
}
const pct = t => t.h ? +(100 * t.p / t.h).toFixed(2) : null;
function semester(n, completed, hours = 5) {
  const days = {}, keys = [];
  let d = new Date('2026-06-15T00:00:00');
  while (keys.length < n) {
    const g = d.getDay();
    if (g !== 0 && g !== 6) { const k = d.toISOString().slice(0, 10); keys.push(k); days[k] = { hours, on: true, done: keys.length <= completed }; }
    d.setDate(d.getDate() + 1);
  }
  return { days, keys };
}
{
  const { days, keys } = semester(90, 20);
  const roster = [{ sid: 'S1' }], marks = {};
  keys.slice(0, 20).forEach(k => marks[k] = { S1: 'PPPPP' });
  eq('A open days excluded', pct(totals(days, roster, marks).S1), 100);
}
{
  const { days, keys } = semester(50, 50);
  const roster = [{ sid: 'S1' }], marks = {};
  keys.forEach((k, i) => marks[k] = { S1: i < 45 ? 'PPPPP' : 'AAAAA' });
  eq('B 45 of 50', pct(totals(days, roster, marks).S1), 90);
}
console.log(pass + ' passed, ' + fail + ' failed');
process.exit(fail ? 1 : 0);
