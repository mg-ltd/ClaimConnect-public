# Physical-device verification

Use simulated incidents and synthetic photographs. Record the app commit, version, device model and Android version. CI emulator checks supplement these checks; they do not establish physical camera, reboot or recipient-app behaviour.

| Check | Expected result |
| --- | --- |
| Fresh install and new incident | Safety notice, guided entry and review work |
| Type, wait for Saved on device, force-stop | Reopen the exact saved draft from history |
| Force-stop immediately during typing | All committed characters remain; no false Saved label before commit |
| Reboot during a saved draft | Incident and photographs remain accessible |
| Camera capture and cancellation | Original imported once; cancellation creates no evidence |
| Kill app while external camera is open | Returning capture is imported or remains recoverable; no silent record loss |
| Picker with 10 photographs | All ten originals retained, hashed and displayed |
| Unsupported, huge and corrupt image | Clear rejection, no corrupt evidence row |
| Photo rotation and front-camera mirroring | Preview and PDF have correct orientation |
| Edit text or photo caption after export | Old pack becomes unavailable; regeneration reflects changes |
| Long account and captions | PDF paginates without truncating content |
| Ten-photo PDF | Complete manifest, hashes and all ten display images |
| PDF viewer absent | Useful message and Save PDF fallback |
| Share, then cancel | No delivery or exported status is claimed |
| Share after confirmation | Only the chosen app receives a readable PDF URI |
| Save to local and cloud document providers | Correct copy is written; failure does not claim success |
| Low storage during capture, autosave and generation | Useful error; existing record survives; Retry save succeeds after freeing storage |
| Delete an incident | History, Room rows, encrypted media, pack and scoped cache copies removed |
| Restart after interrupted import/export/delete | Startup reconciliation removes unreferenced files |
| Large font, dark mode and small display | Fields and actions remain accessible with scrolling |
| TalkBack and keyboard | Labels, photographs and confirmation actions are understandable |
| Device backup/transfer | Records excluded; app never promises recovery |
| Uninstall/reinstall | Empty local history and clear local-only notice |
| Internal Play installation | Signed build installs and core journey works |

The initial usability target is a complete incident and pack in under five minutes, once the scene is safe.
