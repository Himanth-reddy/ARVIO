// Static output from the same compilation as UpdateWatcher. A mutable public
// file can be overwritten by another dev/build process and cause reload loops.
export const dynamic = "force-static";

export function GET() {
  return Response.json({ v: process.env.NEXT_PUBLIC_BUILD_STAMP }, {
    headers: { "Cache-Control": "no-store" }
  });
}
