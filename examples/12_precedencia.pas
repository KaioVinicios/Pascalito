program precedencia;
var
  a, b, c, d: integer;
  r: boolean;
begin
  a := 1 + 2 * 3;
  b := (1 + 2) * 3;
  c := ~a + b * 2 - 1;
  d := a - b - c;
  r := a > b and c < d or ~false
end.
