// The caller has already verified CI provenance and signed all packages locally.
// Injecting transport and byte verification keeps publication ordering testable offline.
export function publishDraft({ gh, repo, tag, release, unsignedNames, names, signedPath, checkedPath, notes, verify }) {
  if (!release.isDraft || release.tagName !== tag) throw new Error('Unsigned release must still be a draft');
  if (release.assets.some(asset => !unsignedNames.includes(asset.name))) throw new Error('Unexpected draft assets; inspect before retrying');
  for (const asset of release.assets) gh(['release', 'delete-asset', tag, asset.name, '-R', repo, '--yes']);
  gh(['release', 'upload', tag, '-R', repo, ...names.map(signedPath)]);
  gh(['release', 'edit', tag, '-R', repo, '--title', `Household Remote ${tag}`, '--notes-file', notes]);
  gh(['release', 'download', tag, '-R', repo, '-D', checkedPath]);
  verify();
  gh(['release', 'edit', tag, '-R', repo, '--draft=false', '--latest']);
}
